/**
 * Extracts field definitions from a Doris/Paimon {@code CREATE TABLE} statement.
 *
 * <p>This is deliberately not a SQL parser. It understands the standard field-definition subset
 * agreed in YOIP-9: a parenthesised body, one field per top-level comma, and a trailing
 * {@code COMMENT}. Everything outside that subset (engine options, partitions, indexes, generated
 * columns) is ignored instead of being reported as an error, because pasting a real Doris DDL must
 * not produce a wall of noise.
 */

/** One field definition located in the statement body. */
export interface DdlField {
  /** Column name with backticks, double quotes and brackets removed. */
  name: string;
  /** Type exactly as written, such as {@code DECIMAL(20,2)}; empty when the fragment has none. */
  rawType: string;
  /** Uppercase base type without arguments; empty when the fragment has none. */
  baseType: string;
  /** Text inside the parentheses or angle brackets, or null when the type has no arguments. */
  typeArgs: string | null;
  /** Unquoted {@code COMMENT}, or null when the field has none. */
  comment: string | null;
  notNull: boolean;
  primaryKey: boolean;
  /** 1-based line in the pasted text, so a rejection points at the right place. */
  line: number;
  /** Trimmed source fragment, shown when the row cannot be used. */
  source: string;
}

export interface DdlParseResult {
  fields: DdlField[];
  errors: string[];
  /** False when no field-definition body could be located at all. */
  found: boolean;
}

const OPEN_QUOTES = new Set(["'", '"', "`"]);

/** Table-level clauses that sit in the body but are not fields. */
const TABLE_CLAUSES =
  /^(PRIMARY\s+KEY|UNIQUE\s+KEY|AGGREGATE\s+KEY|DUPLICATE\s+KEY|DISTRIBUTED\s+KEY|CLUSTERED\s+KEY|UNIQUE\s+INDEX|FULLTEXT\s+INDEX|SPATIAL\s+INDEX|KEY|INDEX|FOREIGN\s+KEY|CONSTRAINT|PARTITION|DISTRIBUTED|PROPERTIES|ENGINE|COMMENT)\b/i;

const PRIMARY_KEY_CLAUSE = /^(?:CONSTRAINT\s+[A-Za-z0-9_`"[\]$]+\s+)?PRIMARY\s+KEY\s*\(([^)]*)\)/i;

const CREATE_TABLE = /\bcreate\s+(?:or\s+replace\s+|external\s+|temporary\s+)*table\b/gi;

/** Container types whose angle brackets protect their inner commas. */
const TYPE_CONTAINER = /\b(?:STRUCT|ARRAY|MAP|ROW)\s*$/i;

/**
 * Replaces SQL comments with spaces while keeping every offset and line break.
 *
 * <p>Comment markers inside quotes stay data. Masking happens before the scanners run because a
 * comment such as {@code -- 金额(含税} would otherwise unbalance the parenthesis depth and make the
 * whole paste look like it has no field body.
 */
function maskComments(text: string) {
  const chars = text.split("");
  let quote: string | null = null;
  for (let index = 0; index < chars.length; index += 1) {
    const char = chars[index];
    if (quote) {
      if (char === "\\") index += 1;
      else if (char === quote) {
        if (chars[index + 1] === quote) index += 1;
        else quote = null;
      }
      continue;
    }
    if (OPEN_QUOTES.has(char)) {
      quote = char;
      continue;
    }
    if (char === "-" && chars[index + 1] === "-") {
      while (index < chars.length && chars[index] !== "\n") {
        chars[index] = " ";
        index += 1;
      }
      continue;
    }
    if (char === "/" && chars[index + 1] === "*") {
      chars[index] = " ";
      chars[index + 1] = " ";
      index += 1;
      while (index + 1 < chars.length && !(chars[index] === "*" && chars[index + 1] === "/")) {
        if (chars[index] !== "\n") chars[index] = " ";
        index += 1;
      }
      if (index + 1 < chars.length) {
        chars[index] = " ";
        chars[index + 1] = " ";
        index += 1;
      }
      continue;
    }
  }
  return chars.join("");
}

/**
 * Locates the outermost parenthesis pair that follows {@code CREATE TABLE}.
 *
 * <p>Returns null when the statement has no body, which is how a paste that only contains a header
 * becomes one whole-statement message instead of a per-line cascade.
 */
function findBody(text: string): { start: number; end: number } | null {
  const create = /create\s+(?:or\s+replace\s+|external\s+|temporary\s+)*table\b/i.exec(text);
  const from = create ? create.index + create[0].length : 0;
  const open = text.indexOf("(", from);
  if (open < 0) return null;
  let depth = 0;
  let quote: string | null = null;
  for (let index = open; index < text.length; index += 1) {
    const char = text[index];
    if (quote) {
      if (char === "\\") index += 1;
      else if (char === quote) {
        if (text[index + 1] === quote) index += 1;
        else quote = null;
      }
      continue;
    }
    if (OPEN_QUOTES.has(char)) {
      quote = char;
      continue;
    }
    if (char === "(") depth += 1;
    else if (char === ")") {
      depth -= 1;
      if (depth === 0) return { start: open + 1, end: index };
    }
  }
  return null;
}

/**
 * Splits the body on commas that are outside parentheses, type containers and quotes.
 *
 * <p>A naive {@code split(',')} breaks {@code DECIMAL(10,2)}, {@code STRUCT<a:int, b:string>} and
 * {@code COMMENT '含,逗号'} at the wrong place. Angle brackets only count after a container type,
 * so a default expression such as {@code 1<2} cannot swallow the next field.
 */
function splitBody(
  text: string,
  bodyStart: number,
  bodyEnd: number,
): { start: number; end: number }[] {
  const parts: { start: number; end: number }[] = [];
  let parens = 0;
  let angles = 0;
  let quote: string | null = null;
  let start = bodyStart;
  for (let index = bodyStart; index < bodyEnd; index += 1) {
    const char = text[index];
    if (quote) {
      if (char === "\\") index += 1;
      else if (char === quote) {
        if (text[index + 1] === quote) index += 1;
        else quote = null;
      }
      continue;
    }
    if (OPEN_QUOTES.has(char)) {
      quote = char;
      continue;
    }
    if (char === "(") parens += 1;
    else if (char === ")") parens = Math.max(0, parens - 1);
    else if (char === "<") {
      if (TYPE_CONTAINER.test(text.slice(Math.max(bodyStart, index - 20), index))) angles += 1;
    } else if (char === ">") angles = Math.max(0, angles - 1);
    else if (char === "," && parens === 0 && angles === 0) {
      parts.push({ start, end: index });
      start = index + 1;
    }
  }
  parts.push({ start, end: bodyEnd });
  return parts;
}

/** Reads one identifier and returns it with the remaining text. */
function readIdentifier(text: string): { value: string; rest: string } | null {
  const leading = text.match(/^\s*/)?.[0].length ?? 0;
  const rest = text.slice(leading);
  const quoted = /^(`([^`]+)`|"([^"]+)"|\[([^\]]+)\])/.exec(rest);
  if (quoted) {
    const value = quoted[2] ?? quoted[3] ?? quoted[4] ?? "";
    return { value, rest: rest.slice(quoted[0].length) };
  }
  const bare = /^([A-Za-z_][A-Za-z0-9_$]*)/.exec(rest);
  if (!bare) return null;
  return { value: bare[1], rest: rest.slice(bare[0].length) };
}

/** Reads the type token, including a balanced argument list and multi-word base names. */
function readType(
  text: string,
): { raw: string; base: string; args: string | null; rest: string } | null {
  const first = readIdentifier(text);
  if (!first) return null;
  let raw = first.value;
  let rest = first.rest;
  const trailingWord = () => {
    const match = /^\s+([A-Za-z_][A-Za-z0-9_]*)/.exec(rest);
    return match ? match[1].toUpperCase() : null;
  };
  const consumeWord = () => {
    const match = /^\s+([A-Za-z_][A-Za-z0-9_]*)/.exec(rest);
    if (!match) return false;
    raw += ` ${match[1]}`;
    rest = rest.slice(match[0].length);
    return true;
  };
  // DOUBLE PRECISION, CHARACTER VARYING and TIMESTAMP WITH TIME ZONE stay one type.
  const word = trailingWord();
  if (word === "PRECISION" || word === "VARYING") consumeWord();
  if (trailingWord() === "WITH" || trailingWord() === "WITHOUT") {
    consumeWord();
    if (trailingWord() === "TIME") consumeWord();
    if (trailingWord() === "ZONE") consumeWord();
  }
  const argsMatch = /^\s*(\([^)]*\)|<[^>]*>)/.exec(rest);
  let args: string | null = null;
  if (argsMatch) {
    args = argsMatch[1].slice(1, -1).trim() || null;
    raw += argsMatch[1];
    rest = rest.slice(argsMatch[0].length);
  }
  return {
    raw: raw.trim(),
    base: raw
      .replace(/[<(].*$/, "")
      .trim()
      .toUpperCase(),
    args,
    rest,
  };
}

/** Quotes are doubled or backslash-escaped inside a SQL string; both forms become one character. */
function unquote(value: string) {
  const quote = value[0];
  return value
    .slice(1, -1)
    .split(quote + quote)
    .join(quote)
    .replace(/\\(.)/g, "$1")
    .trim();
}

/** Extracts an unquoted COMMENT value. */
function readComment(text: string): string | null {
  const match = /COMMENT\s+('(?:\\.|''|[^'\\])*'|"(?:\\.|""|[^"\\])*")/i.exec(text);
  return match ? unquote(match[1]) : null;
}

/**
 * Removes the value clauses before constraint detection.
 *
 * <p>{@code COMMENT 'not null 必填'} and {@code DEFAULT 'NOT NULL'} contain the very words used to
 * detect constraints, so matching them on the raw tail would fabricate primary keys.
 */
function stripValueClauses(text: string) {
  return text
    .replace(/COMMENT\s+('(?:\\.|''|[^'\\])*'|"(?:\\.|""|[^"\\])*")/gi, " ")
    .replace(/DEFAULT\s+('(?:\\.|''|[^'\\])*'|"(?:\\.|""|[^"\\])*"|\([^)]*\)|[^\s,]+)/gi, " ");
}

/**
 * Parses the pasted statement.
 *
 * <p>Failed fragments are reported with their line and source; the successful ones are still
 * returned, because a single malformed line must not discard the whole paste.
 */
export function parseCreateTable(text: string): DdlParseResult {
  const masked = maskComments(text);
  const statements = [...masked.matchAll(CREATE_TABLE)].length;
  const body = findBody(masked);
  if (!body) {
    return {
      fields: [],
      errors: ["未找到字段定义区，请粘贴包含字段定义的 CREATE TABLE 语句"],
      found: false,
    };
  }
  const errors: string[] = [];
  if (statements > 1) errors.push("检测到多条 CREATE TABLE，当前只解析第一条语句");
  const fields: DdlField[] = [];
  const primaryKeyColumns = new Set<string>();
  const lineOf = (position: number) => text.slice(0, position).split("\n").length;
  for (const part of splitBody(masked, body.start, body.end)) {
    const maskedFragment = masked.slice(part.start, part.end);
    const fragment = maskedFragment.trim();
    if (!fragment) continue;
    const source = text.slice(part.start, part.end).trim();
    const line = lineOf(part.start + (maskedFragment.length - maskedFragment.trimStart().length));
    const keyClause = PRIMARY_KEY_CLAUSE.exec(fragment);
    if (keyClause) {
      keyClause[1]
        .split(",")
        .map((column) => column.replace(/[`"[\]\s]/g, "").toLowerCase())
        .filter(Boolean)
        .forEach((column) => primaryKeyColumns.add(column));
      continue;
    }
    if (TABLE_CLAUSES.test(fragment)) continue;
    const identifier = readIdentifier(fragment);
    if (!identifier) {
      errors.push(`第 ${line} 行：无法识别字段名「${source || fragment}」`);
      continue;
    }
    const type = readType(identifier.rest);
    if (!type) {
      // Keep the row so the user can pick a type instead of retyping the column elsewhere.
      fields.push({
        name: identifier.value,
        rawType: "",
        baseType: "",
        typeArgs: null,
        comment: readComment(identifier.rest),
        notNull: false,
        primaryKey: false,
        line,
        source,
      });
      errors.push(`第 ${line} 行：字段「${identifier.value}」缺少数据类型`);
      continue;
    }
    const constraints = stripValueClauses(type.rest);
    fields.push({
      name: identifier.value,
      rawType: type.raw,
      baseType: type.base,
      typeArgs: type.args,
      comment: readComment(type.rest),
      notNull: /\bNOT\s+NULL\b/i.test(constraints),
      primaryKey: /\bPRIMARY\s+KEY\b/i.test(constraints),
      line,
      source,
    });
  }
  if (fields.length === 0 && errors.length === 0) {
    errors.push("字段定义区为空，请检查 CREATE TABLE 语句");
  }
  const normalizedPrimaryKeys = new Set(
    [...primaryKeyColumns].map((column) => column.replace(/[^a-z0-9_]/g, "")),
  );
  for (const field of fields) {
    if (normalizedPrimaryKeys.has(field.name.toLowerCase().replace(/[^a-z0-9_]/g, ""))) {
      field.primaryKey = true;
    }
  }
  return { fields, errors, found: true };
}

/** Splits a type argument list into the size and scale understood by the logical DECIMAL. */
export function typeSize(typeArgs: string | null): { size?: number; scale?: number } {
  const result: { size?: number; scale?: number } = {};
  if (!typeArgs) return result;
  const parts = typeArgs.split(",").map((part) => Number.parseInt(part.trim(), 10));
  if (Number.isFinite(parts[0]) && parts[0] > 0) {
    result.size = parts[0];
    if (parts.length > 1 && Number.isFinite(parts[1]) && parts[1] >= 0) result.scale = parts[1];
  }
  return result;
}
