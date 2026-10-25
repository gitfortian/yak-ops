/**
 * Draft generation for reverse modeling, delivered under YOIP-12.
 *
 * <p>Kept free of runtime service imports so the rules can be tested directly: everything here is a
 * pure translation from physical metadata to editable drafts. The provider wrapper adds the network
 * calls; the wizard and the provider share these functions so a column can never map differently
 * depending on the entry point.
 */
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { ModelField } from "@/service/data-modeling/dimension-modeling/reverse-modeling";
// The extension keeps this module loadable by the Node test runner; the bundler resolves it too.
import { mapPhysicalColumn, normalizeFieldCode } from "./field-mapping.ts";
import type { FieldDraft } from "./fields";

/** Matches the server limits for the Chinese name and the field description. */
export const CN_NAME_MAX = 128;
export const DESCRIPTION_MAX = 500;

let draftSequence = 0;
const nextKey = () => `reverse-field-${(draftSequence += 1)}`;

const truncate = (value: string, max: number) => (value.length > max ? value.slice(0, max) : value);

/**
 * Turns physical columns into editable drafts.
 *
 * <p>A column whose type has no unambiguous logical counterpart keeps its row with an empty type
 * and the reason in {@code importNote}: the user resolves it before saving, exactly as the field
 * import path requires. Duplicate derived codes are reported rather than silently renamed, because
 * renaming would invent a column identity the physical table does not have.
 */
export function describeFieldDrafts(columns: readonly DataSourceCatalogColumn[]): FieldDraft[] {
  const firstByCode = new Map<string, number>();
  return columns.map((column, index) => {
    const mapping = mapPhysicalColumn(column);
    const enCode = normalizeFieldCode(column.name);
    const duplicateOf = firstByCode.get(enCode);
    if (duplicateOf === undefined) firstByCode.set(enCode, index);
    const remarks = column.remarks?.trim() ?? "";
    const notes = [mapping.note];
    if (duplicateOf !== undefined) notes.push(`编码与第 ${duplicateOf + 1} 行重复`);
    const note = notes.filter((value): value is string => Boolean(value)).join("；");
    return {
      key: nextKey(),
      enCode,
      cnName: truncate(remarks || column.name.trim() || enCode, CN_NAME_MAX),
      dataType: mapping.kind === "MAPPED" ? mapping.dataType : null,
      lengthPrecision: mapping.kind === "MAPPED" ? (mapping.lengthPrecision ?? "") : "",
      isPrimaryKey: Boolean(column.primaryKey),
      isNotNull: column.nullable === false,
      isPartition: false,
      aggregateType: null,
      standardId: null,
      standardEnCode: null,
      lookupId: null,
      lookupEnCode: null,
      measureUnitId: null,
      measureUnitEnCode: null,
      metricId: null,
      description: remarks ? truncate(remarks, DESCRIPTION_MAX) : null,
      importNote: note || null,
    };
  });
}

/**
 * Narrows a draft to the declared reverse-modeling field shape.
 *
 * <p>Returns null when the draft has no logical type: the seam's {@code ModelField} cannot express
 * a half-mapped column, so callers that must keep those rows use {@link describeFieldDrafts}
 * directly instead of dropping them at this boundary.
 */
export function toModelField(draft: FieldDraft): ModelField | null {
  if (!draft.dataType) return null;
  return {
    enCode: draft.enCode,
    cnName: draft.cnName,
    dataType: draft.dataType,
    lengthPrecision: draft.lengthPrecision || undefined,
    isPrimaryKey: draft.isPrimaryKey,
    isNotNull: draft.isNotNull,
    description: draft.description ?? undefined,
  };
}

/**
 * Derives the editable table-name suffix from a physical name.
 *
 * <p>The selected layer prefix is stripped when it actually matches, so the subsequent save can
 * re-add it without doubling. A stripped name that still violates the logical naming rule is
 * returned as-is instead of being coerced: the wizard shows the rule and lets the user correct it.
 */
export function draftTableName(physicalName: string, layerEnCode?: string | null): string {
  const name = physicalName.trim();
  const prefix = layerEnCode?.trim().toLowerCase();
  if (!name || !prefix) return name;
  const head = `${prefix}_`;
  if (!name.toLowerCase().startsWith(head)) return name;
  const stripped = name.slice(head.length);
  return /^[A-Za-z][A-Za-z0-9_]*$/.test(stripped) ? stripped.toLowerCase() : stripped;
}

/** Table Chinese name: the physical comment when present, otherwise the physical table name. */
export function draftTableCnName(table: { name: string; remarks?: string | null }): string {
  const remarks = table.remarks?.trim() ?? "";
  return truncate(remarks || table.name.trim(), CN_NAME_MAX);
}
