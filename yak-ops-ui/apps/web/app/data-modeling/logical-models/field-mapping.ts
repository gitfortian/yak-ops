import type { DataType } from "@/service/data-modeling/data-standards/types";
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { FieldDraft } from "./fields";

/**
 * Result of translating one physical column into the five logical types.
 *
 * <p>Only lossless or unambiguous types are mapped automatically. A column that needs a decision
 * keeps its place in the list with an empty type and the reason, instead of being silently coerced.
 */
export type PhysicalMapping =
  | { kind: "MAPPED"; dataType: DataType; lengthPrecision: string | null; note: string | null }
  | { kind: "REVIEW"; note: string }
  | { kind: "UNSUPPORTED"; note: string };

const STRING_TYPES = new Set([
  "VARCHAR",
  "VARCHAR2",
  "NVARCHAR",
  "NVARCHAR2",
  "CHAR",
  "NCHAR",
  "TEXT",
  "STRING",
  "CLOB",
  "NCLOB",
]);
const INTEGER_TYPES = new Set(["TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT"]);
const DECIMAL_TYPES = new Set(["DECIMAL", "NUMERIC", "NUMBER"]);
const TIMESTAMP_TYPES = new Set([
  "DATETIME",
  "TIMESTAMP",
  "TIMESTAMPTZ",
  "TIMESTAMP WITH TIME ZONE",
]);
const TEXTUAL_JSON_TYPES = new Set(["JSON", "JSONB"]);
const BOOLEAN_TYPES = new Set(["BOOLEAN", "BOOL", "BIT"]);
const FLOAT_TYPES = new Set(["FLOAT", "DOUBLE", "REAL", "DOUBLE PRECISION"]);
const BINARY_TYPES = new Set([
  "BLOB",
  "TINYBLOB",
  "MEDIUMBLOB",
  "LONGBLOB",
  "BINARY",
  "VARBINARY",
  "BYTEA",
  "IMAGE",
]);

/** Strips size suffixes and modifiers so {@code VARCHAR(64)} and {@code varchar} share one rule. */
export function normalizePhysicalType(typeName: string | null | undefined) {
  if (!typeName) return "";
  return typeName
    .toUpperCase()
    .replace(/\(.*$/, "")
    .replace(/\bUNSIGNED\b/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function mapPhysicalColumn(column: DataSourceCatalogColumn): PhysicalMapping {
  const type = normalizePhysicalType(column.typeName);
  if (!type) return { kind: "REVIEW", note: "数据库未返回字段类型，请手动选择" };
  if (STRING_TYPES.has(type)) {
    const length = column.size && column.size > 0 ? String(column.size) : null;
    return {
      kind: "MAPPED",
      dataType: "STRING",
      lengthPrecision: length,
      note: null,
    };
  }
  if (INTEGER_TYPES.has(type))
    return { kind: "MAPPED", dataType: "BIGINT", lengthPrecision: null, note: null };
  if (DECIMAL_TYPES.has(type)) {
    const precision =
      column.size && column.scale != null
        ? `${column.size},${column.scale}`
        : column.size
          ? String(column.size)
          : null;
    return { kind: "MAPPED", dataType: "DECIMAL", lengthPrecision: precision, note: null };
  }
  if (type === "DATE")
    return { kind: "MAPPED", dataType: "DATE", lengthPrecision: null, note: null };
  if (TIMESTAMP_TYPES.has(type))
    return { kind: "MAPPED", dataType: "TIMESTAMP", lengthPrecision: null, note: null };
  if (TEXTUAL_JSON_TYPES.has(type))
    return {
      kind: "MAPPED",
      dataType: "STRING",
      lengthPrecision: null,
      note: "JSON 以文本保存，不解析语义",
    };
  if (BOOLEAN_TYPES.has(type))
    return { kind: "REVIEW", note: "逻辑类型没有布尔，建议选 STRING 存 true/false" };
  if (FLOAT_TYPES.has(type))
    return { kind: "REVIEW", note: "收敛到 DECIMAL 需要你指定精度，系统不替用户猜测" };
  if (BINARY_TYPES.has(type))
    return { kind: "UNSUPPORTED", note: "没有二进制逻辑类型，请删除该字段或改为受支持类型" };
  return { kind: "UNSUPPORTED", note: `不支持物理类型 ${type}，请删除该字段或改为受支持类型` };
}

/**
 * Turns a physical name into a legal logical code: upper-case Oracle names and camelCase both
 * collapse to snake_case, and a leading digit is repaired instead of rejected.
 */
export function normalizeFieldCode(name: string) {
  const snake = name
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "");
  if (!snake) return "field";
  return /^[a-z]/.test(snake) ? snake.slice(0, 64) : `f_${snake}`.slice(0, 64);
}

/** Builds one editable row; unmapped columns keep an empty type and their reason. */
export function columnToField(
  column: DataSourceCatalogColumn,
  mapping: PhysicalMapping,
  key: string,
): FieldDraft {
  return {
    key,
    cnName: column.remarks?.trim() || column.name,
    enCode: normalizeFieldCode(column.name),
    dataType: mapping.kind === "MAPPED" ? mapping.dataType : null,
    lengthPrecision: mapping.kind === "MAPPED" ? (mapping.lengthPrecision ?? "") : "",
    isPrimaryKey: Boolean(column.primaryKey),
    isNotNull: column.nullable === false,
    isPartition: false,
    standardId: null,
    standardEnCode: null,
    description: null,
    importNote: mapping.note,
  };
}
