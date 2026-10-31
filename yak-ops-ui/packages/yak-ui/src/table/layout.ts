import type { CSSProperties } from "react";

import type { TableColumn } from "./interface";

const FALLBACK_WIDTH = 160;

/** Resolves a pixel width literal; percentages and keywords stay unresolved. */
export const pixelWidth = (
  value: CSSProperties["width"] | true | undefined,
): number | undefined => {
  if (typeof value === "number") return Number.isFinite(value) && value > 0 ? value : undefined;
  if (typeof value !== "string") return undefined;
  const match = /^(\d+(?:\.\d+)?)px$/.exec(value.trim());
  return match ? Number(match[1]) : undefined;
};

export interface ResolvedTableLayout {
  widths: number[] | null;
  tableWidth?: number;
}

/**
 * Replaces resolved widths with caller overrides, keeping null entries automatic.
 *
 * @param widths resolved pixel widths of one layout pass
 * @param overrides caller-held widths aligned by column index; null keeps the resolved value
 * @returns the merged widths, or the input when no override is present
 */
export function applyColumnWidthOverrides(
  widths: number[] | null,
  overrides: readonly (number | null)[] | null | undefined,
): number[] | null {
  if (!widths || !overrides?.some((item) => item != null)) return widths;
  const merged = [...widths];
  overrides.forEach((value, index) => {
    if (value != null && index < merged.length) merged[index] = value;
  });
  return merged;
}

/** Share explicit pixel widths across header/body and sticky offsets. */
export function resolveTableLayout<RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  viewportWidth: number,
  scrollX?: CSSProperties["minWidth"] | true,
): ResolvedTableLayout {
  const definitions = columns.map((column) => {
    const value = column.width ?? column.minWidth;
    const width = pixelWidth(value);
    if (value != null && width == null) return null;
    return { width: width ?? FALLBACK_WIDTH, flexible: column.width == null };
  });
  if (definitions.some((item) => item == null)) return { widths: null };
  const widths = definitions.map((item) => item!.width);
  const flexible = definitions.flatMap((item, index) => (item!.flexible ? [index] : []));
  const minimum = widths.reduce((sum, width) => sum + width, 0);
  // Never stretch explicitly fixed columns simply to fill the viewport.
  const available = flexible.length
    ? Math.max(minimum, viewportWidth, pixelWidth(scrollX) ?? 0)
    : minimum;
  const extra = (available - minimum) / (flexible.length || 1);
  for (const index of flexible) widths[index] += extra;
  return { widths, tableWidth: widths.reduce((sum, width) => sum + width, 0) };
}
