import type { CSSProperties } from "react";

import type { TableColumn } from "./interface";

const FALLBACK_WIDTH = 160;

const pixels = (value: CSSProperties["width"] | true | undefined): number | undefined => {
  if (typeof value === "number") return Number.isFinite(value) && value > 0 ? value : undefined;
  if (typeof value !== "string") return undefined;
  const match = /^(\\d+(?:\\.\\d+)?)px$/.exec(value.trim());
  return match ? Number(match[1]) : undefined;
};

export interface ResolvedTableLayout {
  widths: number[] | null;
  tableWidth?: number;
}

/** Share explicit pixel widths across header/body and sticky offsets. */
export function resolveTableLayout<RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  viewportWidth: number,
  scrollX?: CSSProperties["minWidth"] | true,
): ResolvedTableLayout {
  const definitions = columns.map((column) => {
    const value = column.width ?? column.minWidth;
    const width = pixels(value);
    if (value != null && width == null) return null;
    return { width: width ?? FALLBACK_WIDTH, flexible: column.width == null };
  });
  if (definitions.some((item) => item == null)) return { widths: null };
  const widths = definitions.map((item) => item!.width);
  const flexible = definitions.flatMap((item, index) => (item!.flexible ? [index] : []));
  const minimum = widths.reduce((sum, width) => sum + width, 0);
  // Never stretch explicitly fixed columns simply to fill the viewport.
  const available = flexible.length
    ? Math.max(minimum, viewportWidth, pixels(scrollX) ?? 0)
    : minimum;
  const extra = (available - minimum) / (flexible.length || 1);
  for (const index of flexible) widths[index] += extra;
  return { widths, tableWidth: widths.reduce((sum, width) => sum + width, 0) };
}
