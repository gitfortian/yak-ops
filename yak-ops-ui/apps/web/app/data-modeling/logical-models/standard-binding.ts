import type { StandardField } from "@/service/data-modeling/data-standards/types";
import { normalizeFieldCode } from "./field-mapping.ts";
import { newFieldKey } from "./field-key.ts";
import type { FieldDraft } from "./fields";

/**
 * Attributes a bound standard owns.
 *
 * <p>The server rewrites code, Chinese name, type and length from the standard on every save, so a
 * draft must show those values as soon as the standard is bound. Nullability is only a default: the
 * submitted flag wins, because the same standard is cited by layers whose data carries different
 * constraints.
 */
export function inheritStandard(standard: StandardField) {
  return {
    cnName: standard.cnName,
    enCode: standard.enCode,
    dataType: standard.dataType,
    lengthPrecision: standard.lengthPrecision ?? "",
    isNotNull: standard.isNotNull,
    standardId: standard.id,
    standardEnCode: standard.enCode,
  };
}

/** A binding that would move the code away from the physical column it was imported from. */
export interface StandardBindingWarning {
  standardCnName: string;
  sourceTable: string;
  sourceColumn: string;
  currentCode: string;
  nextCode: string;
}

/**
 * Detects the point where binding a standard detaches an imported row from its source column.
 *
 * <p>Returns null for hand-added rows, for a binding that keeps the current code, and for a code
 * that lands back on the physical column; only a real source-code break needs confirmation.
 */
export function standardBindingWarning(
  row: FieldDraft,
  standard: StandardField,
): StandardBindingWarning | null {
  const source = row.source;
  if (!source) return null;
  const currentCode = row.enCode.trim();
  const nextCode = standard.enCode.trim();
  if (!nextCode || nextCode === currentCode) return null;
  if (nextCode === normalizeFieldCode(source.column)) return null;
  return {
    standardCnName: standard.cnName,
    sourceTable: source.table,
    sourceColumn: source.column,
    currentCode,
    nextCode,
  };
}

/** Row-level trace of a confirmed source-code break; rendered beside the row for this session. */
export function bindingWarningNote(warning: StandardBindingWarning) {
  return `已绑定标准「${warning.standardCnName}」：英文编码由 ${warning.currentCode} 改为 ${warning.nextCode}，与来源表 ${warning.sourceTable} 的列 ${warning.sourceColumn} 不一致，下游字段映射需同步调整`;
}

/** A new row created from a standard; a standard carries no description of its own. */
export function fromStandard(standard: StandardField): FieldDraft {
  return {
    key: newFieldKey(),
    ...inheritStandard(standard),
    isPrimaryKey: false,
    isPartition: false,
    aggregateType: null,
    lookupId: null,
    lookupEnCode: null,
    measureUnitId: null,
    measureUnitEnCode: null,
    metricId: null,
    description: null,
    importNote: null,
    source: null,
  };
}

/** Binds a standard to an existing row, showing exactly what the next save will persist. */
export function applyStandard(
  row: FieldDraft,
  standard: StandardField,
  importNote: string | null = null,
): FieldDraft {
  return { ...row, ...inheritStandard(standard), importNote };
}
