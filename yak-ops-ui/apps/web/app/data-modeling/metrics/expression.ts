/**
 * Pure client-side check of the composite-metric expression grammar, mirroring the server parser:
 * arithmetic (`+ - * /`), parentheses, numbers, whitespace and derived-metric codes. It never
 * validates that a referenced code exists -- the server answers that on save.
 */
const CODE = /^[a-z][a-z0-9_]{0,63}$/;

function tokenize(expression: string): string[] | null {
  const tokens: string[] = [];
  let index = 0;
  while (index < expression.length) {
    const current = expression[index];
    if (/\s/.test(current)) {
      index += 1;
      continue;
    }
    if ("+-*/()".includes(current)) {
      tokens.push(current);
      index += 1;
      continue;
    }
    if (/[0-9.]/.test(current)) {
      const start = index;
      let dots = 0;
      while (index < expression.length && /[0-9.]/.test(expression[index])) {
        if (expression[index] === "." && (dots += 1) > 1) return null;
        index += 1;
      }
      const literal = expression.slice(start, index);
      if (literal === ".") return null;
      tokens.push(literal);
      continue;
    }
    if (/[a-z]/.test(current)) {
      const start = index;
      while (index < expression.length && /[a-z0-9_]/.test(expression[index])) index += 1;
      tokens.push(expression.slice(start, index));
      continue;
    }
    return null;
  }
  return tokens.length ? tokens : null;
}

/**
 * Returns the referenced codes in first-appearance order, or null when the expression is blank,
 * malformed or carries a token outside the grammar.
 */
export function parseMetricExpression(expression: string): string[] | null {
  if (!expression.trim()) return null;
  const tokens = tokenize(expression);
  if (!tokens) return null;
  const references: string[] = [];
  let cursor = 0;
  const peek = () => tokens[cursor];
  const parseExpression = (): boolean => {
    if (!parseTerm()) return false;
    while (peek() === "+" || peek() === "-") {
      cursor += 1;
      if (!parseTerm()) return false;
    }
    return true;
  };
  const parseTerm = (): boolean => {
    if (!parseFactor()) return false;
    while (peek() === "*" || peek() === "/") {
      cursor += 1;
      if (!parseFactor()) return false;
    }
    return true;
  };
  const parseFactor = (): boolean => {
    const token = peek();
    if (token === undefined) return false;
    if (token === "+" || token === "-") {
      cursor += 1;
      return parseFactor();
    }
    if (token === "(") {
      cursor += 1;
      if (!parseExpression()) return false;
      if (peek() !== ")") return false;
      cursor += 1;
      return true;
    }
    if (/^\d+(\.\d+)?$/.test(token) || CODE.test(token)) {
      if (CODE.test(token)) references.push(token);
      cursor += 1;
      return true;
    }
    return false;
  };
  if (!parseExpression() || cursor !== tokens.length) return null;
  return references;
}

/** The codes an expression cites, deduplicated in first-appearance order; null when malformed. */
export function metricExpressionReferences(expression: string): string[] | null {
  const references = parseMetricExpression(expression);
  if (!references) return null;
  return [...new Set(references)];
}
