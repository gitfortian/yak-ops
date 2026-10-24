import {
  DATA_TYPES,
  type DataType,
  type StandardField,
} from "@/service/data-modeling/data-standards/types";
import type {
  AggregateType,
  ModelField,
  ModelFieldInput,
} from "@/service/data-modeling/dimension-modeling/types";
import { parseCreateTable, type DdlField, typeSize } from "./ddl-parser";
import { mapPhysicalColumn, normalizeFieldCode, type PhysicalMapping } from "./field-mapping";

/** One editable field row; {@code key} keeps React identity stable across reorders. */
export interface FieldDraft {
  key: string;
  cnName: string;
  enCode: string;
  dataType: DataType | null;
  lengthPrecision: string;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  isPartition: boolean;
  aggregateType: AggregateType | null;
  standardId: string | null;
  standardEnCode: string | null;
  /** Optional business description; loaded from and submitted with the model field. */
  description: string | null;
  /** Why an imported column still has no logical type; shown beside the row and never submitted. */
  importNote: string | null;
}

export type FieldIssueField = "cnName" | "enCode" | "dataType" | "partition";

export interface FieldIssue {
  index: number;
  field: FieldIssueField;
  message: string;
}

export const FIELD_CODE = /^[a-z][a-z0-9_]{0,63}$/;
export const TYPE_ITEMS = DATA_TYPES.map((value) => ({ value, label: value }));

/**
 * Bound the model editor enforces on the table-name suffix, before the layer prefix is added.
 *
 * <p>Shared so a second creation path cannot accept a suffix the editor would then refuse to save.
 */
export const MODEL_SUFFIX_MAX = 41;

export const MODEL_SUFFIX_RULE = "表名后缀须以小写字母开头，仅含小写字母、数字或下划线，最多 41 位";

export function isValidModelSuffix(value: string): boolean {
  return FIELD_CODE.test(value) && value.length <= MODEL_SUFFIX_MAX;
}

let fieldSequence = 0;
const nextKey = () => `field-${(fieldSequence += 1)}`;

/** Identity for a row created outside this module, such as an imported column. */
export const newFieldKey = () => nextKey();

export function emptyField(): FieldDraft {
  return {
    key: nextKey(),
    cnName: "",
    enCode: "",
    dataType: "STRING",
    lengthPrecision: "",
    isPrimaryKey: false,
    isNotNull: false,
    isPartition: false,
    aggregateType: null,
    standardId: null,
    standardEnCode: null,
    description: null,
    importNote: null,
  };
}

export function fromModelField(field: ModelField): FieldDraft {
  return {
    key: nextKey(),
    cnName: field.cnName,
    enCode: field.enCode,
    dataType: field.dataType,
    lengthPrecision: field.lengthPrecision ?? "",
    isPrimaryKey: field.isPrimaryKey,
    isNotNull: field.isNotNull,
    isPartition: field.isPartition,
    aggregateType: field.aggregateType,
    standardId: field.standardId,
    standardEnCode: field.standardEnCode,
    description: field.description,
    importNote: null,
  };
}

export function fromStandard(standard: StandardField): FieldDraft {
  return {
    key: nextKey(),
    cnName: standard.cnName,
    enCode: standard.enCode,
    dataType: standard.dataType,
    lengthPrecision: standard.lengthPrecision ?? "",
    isPrimaryKey: false,
    isNotNull: standard.isNotNull,
    isPartition: false,
    aggregateType: null,
    standardId: standard.id,
    standardEnCode: standard.enCode,
    description: null,
    importNote: null,
  };
}

export function toModelFieldInput(row: FieldDraft): ModelFieldInput {
  return {
    enCode: row.enCode.trim(),
    cnName: row.cnName.trim(),
    dataType: row.dataType,
    lengthPrecision: row.lengthPrecision.trim() || null,
    isPrimaryKey: row.isPrimaryKey,
    isNotNull: row.isNotNull,
    isPartition: row.isPartition,
    aggregateType: row.aggregateType,
    standardId: row.standardId,
    description: row.description,
  };
}

/** Mirrors the server rules so a mistake is reported before the round trip. */
export function validateFieldRows(rows: FieldDraft[]): FieldIssue[] {
  const issues: FieldIssue[] = [];
  const firstByCode = new Map<string, number>();
  let partitionIndex = -1;
  rows.forEach((row, index) => {
    if (!row.cnName.trim()) issues.push({ index, field: "cnName", message: "请输入字段中文名" });
    if (row.cnName.trim().length > 128)
      issues.push({ index, field: "cnName", message: "字段中文名不能超过128个字符" });
    const code = row.enCode.trim();
    if (!row.standardId && !FIELD_CODE.test(code))
      issues.push({
        index,
        field: "enCode",
        message: "须以小写字母开头，仅含小写字母、数字或下划线",
      });
    if (!row.standardId && !row.dataType)
      issues.push({ index, field: "dataType", message: "请选择数据类型" });
    if (code) {
      const first = firstByCode.get(code);
      if (first === undefined) firstByCode.set(code, index);
      else issues.push({ index, field: "enCode", message: `与第 ${first + 1} 行编码重复` });
    }
    if (row.isPartition) {
      if (row.dataType !== "STRING")
        issues.push({ index, field: "partition", message: "分区字段类型必须为 STRING" });
      if (partitionIndex >= 0)
        issues.push({
          index,
          field: "partition",
          message: `分区字段只能有一个，第 ${partitionIndex + 1} 行已设置`,
        });
      else partitionIndex = index;
    }
  });
  return issues;
}

const TYPE_ALIASES: Record<string, DataType> = {
  字符串: "STRING",
  文本: "STRING",
  整数: "BIGINT",
  小数: "DECIMAL",
  数值: "DECIMAL",
  日期: "DATE",
  时间戳: "TIMESTAMP",
};

export interface ParsedFields {
  rows: FieldDraft[];
  errors: string[];
  /** Tells the dialog which syntax the paste was read as, so the hint matches the input. */
  mode: "DDL" | "LINES";
}

/**
 * Reads either a {@code CREATE TABLE} paste or the compact one-field-per-line format.
 *
 * <p>The DDL path is preferred whenever the text contains {@code CREATE TABLE}; the line format
 * stays available because it is the fastest way to type a handful of fields by hand.
 */
export function parseFields(text: string, existing: FieldDraft[] = []): ParsedFields {
  if (/\bcreate\s+(?:or\s+replace\s+|external\s+|temporary\s+)*table\b/i.test(text))
    return parseDdl(text, existing);
  return parseFieldLines(text, existing);
}

/**
 * Turns parsed DDL fields into editable rows, reusing the physical type rules.
 *
 * <p>A field whose type cannot be mapped keeps its place with an empty logical type, exactly like
 * an imported catalog column, so the user resolves it before saving instead of losing it here.
 */
function parseDdl(text: string, existing: FieldDraft[]): ParsedFields {
  const parsed = parseCreateTable(text);
  if (!parsed.found) return { rows: [], errors: parsed.errors, mode: "DDL" };
  const rows: FieldDraft[] = [];
  const errors = [...parsed.errors];
  const taken = new Set(existing.map((row) => row.enCode.trim()));
  for (const field of parsed.fields) {
    const enCode = normalizeFieldCode(field.name);
    if (taken.has(enCode)) {
      errors.push(`第 ${field.line} 行：编码「${enCode}」在本模型中重复`);
      continue;
    }
    taken.add(enCode);
    const size = typeSize(field.typeArgs);
    const mapping = mapPhysicalColumn({
      name: field.name,
      typeName: field.baseType,
      size: size.size,
      scale: size.scale,
      nullable: !field.notNull,
      primaryKey: field.primaryKey,
      remarks: field.comment ?? undefined,
    });
    const comment = field.comment?.trim() ?? "";
    const notes = [ddlNote(mapping, field)].filter((note): note is string => Boolean(note));
    if (!comment) notes.push("缺少 COMMENT，中文名暂用字段名");
    else if (comment.length > 128) notes.push("COMMENT 超过 128 字符，中文名已截断");
    rows.push({
      ...emptyField(),
      enCode,
      cnName: (comment || field.name).slice(0, 128),
      dataType: mapping.kind === "MAPPED" ? mapping.dataType : null,
      lengthPrecision: mapping.kind === "MAPPED" ? (mapping.lengthPrecision ?? "") : "",
      isPrimaryKey: field.primaryKey,
      isNotNull: field.notNull,
      description: field.comment,
      importNote: notes.length ? notes.join("；") : null,
    });
  }
  return { rows, errors, mode: "DDL" };
}

/** Keeps the useful decision hint but drops wording that assumes a live catalog read. */
function ddlNote(mapping: PhysicalMapping, field: DdlField) {
  if (mapping.kind === "MAPPED") return mapping.note;
  if (!field.baseType) return "未声明数据类型，请手动选择";
  if (mapping.kind === "REVIEW" && mapping.note.includes("数据库未返回"))
    return "未声明完整的 DECIMAL 精度与小数位，请手动确认";
  return mapping.note;
}

/**
 * Parses one field per line.
 *
 * <p>Columns are {@code enCode, cnName, dataType[, lengthPrecision]} separated by a tab or comma; a
 * header line naming the columns is skipped. Every rejected line is reported with its line number
 * instead of being dropped silently.
 */
export function parseFieldLines(text: string, existing: FieldDraft[] = []): ParsedFields {
  const rows: FieldDraft[] = [];
  const errors: string[] = [];
  const taken = new Set(existing.map((row) => row.enCode.trim()));
  const lines = text.split(/\r?\n/);
  lines.forEach((line, index) => {
    const lineNo = index + 1;
    const trimmed = line.trim();
    if (!trimmed) return;
    if (index === 0 && /编码|encode/i.test(trimmed) && /中文名|name/i.test(trimmed)) return;
    const cells = trimmed.split(/\t|,|，/).map((cell) => cell.trim());
    if (cells.length < 3) {
      errors.push(`第 ${lineNo} 行：至少需要「英文编码, 中文名, 数据类型」三列`);
      return;
    }
    const [enCode, cnName, rawType] = cells;
    // A decimal precision such as 20,2 carries the delimiter itself, so the tail is rejoined.
    const length = cells.slice(3).join(",").replace(/^"|"$/g, "").trim();
    if (!FIELD_CODE.test(enCode)) {
      errors.push(`第 ${lineNo} 行：编码「${enCode}」须以小写字母开头，仅含小写字母、数字或下划线`);
      return;
    }
    if (taken.has(enCode)) {
      errors.push(`第 ${lineNo} 行：编码「${enCode}」在本模型中重复`);
      return;
    }
    if (!cnName) {
      errors.push(`第 ${lineNo} 行：缺少中文名`);
      return;
    }
    const upper = rawType.toUpperCase();
    const dataType = DATA_TYPES.find((type) => type === upper) ?? TYPE_ALIASES[rawType];
    if (!dataType) {
      errors.push(`第 ${lineNo} 行：无法识别数据类型「${rawType}」`);
      return;
    }
    if (length && dataType !== "STRING" && dataType !== "DECIMAL") {
      errors.push(`第 ${lineNo} 行：长度/精度只适用于 STRING 或 DECIMAL`);
      return;
    }
    taken.add(enCode);
    rows.push({ ...emptyField(), enCode, cnName, dataType, lengthPrecision: length });
  });
  return { rows, errors, mode: "LINES" };
}
