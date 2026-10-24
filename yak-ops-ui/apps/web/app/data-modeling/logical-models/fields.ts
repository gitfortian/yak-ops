import {
  DATA_TYPES,
  type DataType,
  type StandardField,
} from "@/service/data-modeling/data-standards/types";
import type { ModelField, ModelFieldInput } from "@/service/data-modeling/dimension-modeling/types";

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
  standardId: string | null;
  standardEnCode: string | null;
  /**
   * Carried through untouched: the table is replaced as a whole on save, so dropping the value here
   * would erase a description written through another client.
   */
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
  return { rows, errors };
}
