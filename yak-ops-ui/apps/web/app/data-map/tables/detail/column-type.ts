/**
 * Renders a column type the way a DBA writes it: a length for character types, a precision and
 * scale for exact numerics, and nothing for everything else.
 *
 * <p>The API omits null properties (`spring.jackson.default-property-inclusion: non_null`), so a
 * character column arrives with no `scale` key at all rather than `scale: null`. Every branch below
 * therefore tests for an actual finite number instead of comparing against null, which is what
 * produced `VARCHAR(32,undefined)` before.
 */

/** Exact numeric types whose parenthesised form is `(precision,scale)`. */
const SCALED_TYPES = /^(DECIMAL|NUMERIC|NUMBER|DEC|FIXED)$/i;

/** Character and binary types whose parenthesised form is a length. */
const LENGTH_TYPES = /^(N?CHAR|N?VARCHAR|NCHAR|NVARCHAR|BINARY|VARBINARY|RAW)$/i;

/** Families that colour the type chip; anything unlisted falls back to neutral. */
const CHARACTER_FAMILY =
  /^(N?CHAR|N?VARCHAR|NCHAR|NVARCHAR|TEXT|TINYTEXT|MEDIUMTEXT|LONGTEXT|CLOB|STRING|ENUM|SET)$/i;
const NUMERIC_FAMILY =
  /^(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL|BIT|DEC|FIXED|SERIAL)$/i;
const TEMPORAL_FAMILY = /^(DATE|TIME|DATETIME|TIMESTAMP|YEAR|INTERVAL)$/i;

/** Families that colour the type chip in the field list. */
export type ColumnTypeFamily = "character" | "numeric" | "temporal" | "other";

/**
 * Classifies a native type for display.
 *
 * @param typeName engine-reported type name
 * @returns the colour family the chip should use
 */
export function columnTypeFamily(typeName?: string | null): ColumnTypeFamily {
  const type = (typeName ?? "").trim();
  if (CHARACTER_FAMILY.test(type)) return "character";
  if (NUMERIC_FAMILY.test(type)) return "numeric";
  if (TEMPORAL_FAMILY.test(type)) return "temporal";
  return "other";
}

export interface ColumnTypeInput {
  typeName?: string | null;
  size?: number | null;
  scale?: number | null;
}

const finiteNumber = (value: unknown): number | null =>
  typeof value === "number" && Number.isFinite(value) ? value : null;

/**
 * Returns the display form of one column type.
 *
 * @param column the type name with its optional size and scale
 * @returns e.g. `VARCHAR(32)`, `DECIMAL(18,2)`, `INT`, or `-` when the type is unknown
 */
export function formatColumnType(column: ColumnTypeInput): string {
  const type = (column.typeName ?? "").trim();
  if (type.length === 0) return "-";

  const size = finiteNumber(column.size);
  if (size === null || size <= 0) return type;

  if (SCALED_TYPES.test(type)) {
    const scale = finiteNumber(column.scale);
    return scale === null || scale < 0 ? `${type}(${size})` : `${type}(${size},${scale})`;
  }
  return LENGTH_TYPES.test(type) ? `${type}(${size})` : type;
}
