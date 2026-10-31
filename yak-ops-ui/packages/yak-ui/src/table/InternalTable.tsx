import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from "react";

import { cn } from "../cn";
import { Empty } from "../empty";
import { Pagination } from "../pagination";
import { Skeleton } from "../skeleton";
import { useFilter } from "./hooks/useFilter";
import { usePagination } from "./hooks/usePagination";
import { useSelection } from "./hooks/useSelection";
import { useSorter } from "./hooks/useSorter";
import type {
  TableAlign,
  TableChangeAction,
  TableColumn,
  TableFilters,
  TablePaginationConfig,
  TablePaginationState,
  TableProps,
  TableSize,
  TableSorterResult,
} from "./interface";
import { applyColumnWidthOverrides, pixelWidth, resolveTableLayout } from "./layout";
import { getTableColumnKey, resolveTableRowKey } from "./utils";

const MIN_RESIZE_WIDTH = 80;

const sizeClasses: Record<TableSize, { header: string; cell: string }> = {
  small: {
    header: "h-10 px-4 text-xs font-medium",
    cell: "px-4 py-3 text-sm",
  },
  medium: {
    header: "h-10 px-3 text-xs",
    cell: "px-3 py-2.5 text-[13px]",
  },
  large: {
    header: "h-12 px-4 text-[13px]",
    cell: "px-4 py-3 text-sm",
  },
};

const alignClasses: Record<TableAlign, string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
};

const getColumnStyle = <RecordType extends object>(
  column: TableColumn<RecordType>,
  index: number,
  widths: number[] | null,
): CSSProperties => {
  const width = widths?.[index];
  if (width != null) return { width, minWidth: width, maxWidth: width };
  return {
    width: column.width,
    minWidth: column.width ?? column.minWidth,
    maxWidth: column.width,
  };
};

const toCssLength = (width: CSSProperties["width"]): string | undefined => {
  if (typeof width === "number") return `${width}px`;
  return typeof width === "string" ? width : undefined;
};

const getFixedOffset = <RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  index: number,
  side: "left" | "right",
  resolvedWidths: number[] | null,
): CSSProperties["left"] => {
  const indexes =
    side === "left"
      ? Array.from({ length: index }, (_, offset) => offset)
      : Array.from({ length: columns.length - index - 1 }, (_, offset) => index + offset + 1);
  const offsets = indexes
    .filter((columnIndex) => columns[columnIndex]?.fixed === side)
    .map((columnIndex) => resolvedWidths?.[columnIndex] ?? toCssLength(columns[columnIndex]?.width))
    .filter((width): width is number | string => width != null);
  if (offsets.length === 0) return 0;
  if (offsets.every((width) => typeof width === "number")) {
    return (offsets as number[]).reduce((sum, width) => sum + width, 0);
  }
  return `calc(${offsets.map((width) => toCssLength(width) ?? width).join(" + ")})`;
};

const getCellStyle = <RecordType extends object>(
  column: TableColumn<RecordType>,
  index: number,
  columns: readonly TableColumn<RecordType>[],
  widths: number[] | null,
): CSSProperties => ({
  ...getColumnStyle(column, index, widths),
  ...(column.fixed === "left" ? { left: getFixedOffset(columns, index, "left", widths) } : {}),
  ...(column.fixed === "right" ? { right: getFixedOffset(columns, index, "right", widths) } : {}),
});

const fixedEdgeShadow = <RecordType extends object>(
  columns: readonly TableColumn<RecordType>[],
  index: number,
): string | undefined => {
  const column = columns[index];
  if (column?.fixed === "left" && columns[index + 1]?.fixed !== "left") {
    return "shadow-[4px_0_8px_-6px_rgba(16,24,40,0.24)]";
  }
  if (column?.fixed === "right" && columns[index - 1]?.fixed !== "right") {
    return "shadow-[-4px_0_8px_-6px_rgba(16,24,40,0.24)]";
  }
  return undefined;
};

const getCellTitle = (content: ReactNode): string | undefined =>
  typeof content === "string" || typeof content === "number" ? String(content) : undefined;

export function InternalTable<RecordType extends object>({
  bordered = false,
  className,
  columns,
  dataSource = [],
  emptyText,
  footer,
  loading = false,
  onChange,
  onRow,
  pagination,
  rowHoverable = true,
  rowHeight,
  rowKey,
  rowSelection,
  scroll,
  size = "medium",
  sticky = false,
  enableColumnResize = false,
  columnWidths,
  onColumnWidthsChange,
}: TableProps<RecordType>) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const [viewportWidth, setViewportWidth] = useState(0);
  const filtersRef = useRef<TableFilters>({});
  const sorterRef = useRef<TableSorterResult<RecordType>>({});
  const paginationStateRef = useRef<TablePaginationState | false>(false);
  const filterDataRef = useRef<
    (data: readonly RecordType[], filtersOverride?: TableFilters) => readonly RecordType[]
  >((data) => data);
  const sortDataRef = useRef<
    (
      data: readonly RecordType[],
      sorterOverride?: TableSorterResult<RecordType>,
    ) => readonly RecordType[]
  >((data) => data);

  useLayoutEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const measure = () => {
      const width = viewport.clientWidth;
      setViewportWidth((current) => (current === width ? current : width));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    return () => observer.disconnect();
  }, []);

  const emitChange = (
    action: TableChangeAction,
    nextFilters: TableFilters,
    nextSorter: TableSorterResult<RecordType>,
    paginationOverride?: Pick<TablePaginationState, "current" | "pageSize">,
  ) => {
    if (!onChange) return;

    const currentDataSource = sortDataRef.current(
      filterDataRef.current(dataSource, nextFilters),
      nextSorter,
    );

    let nextPagination = paginationStateRef.current;
    if (nextPagination !== false) {
      nextPagination = {
        ...nextPagination,
        current: paginationOverride?.current ?? nextPagination.current,
        pageSize: paginationOverride?.pageSize ?? nextPagination.pageSize,
        total:
          pagination !== false && pagination?.total != null
            ? pagination.total
            : currentDataSource.length,
      };
    }

    onChange(nextPagination, nextFilters, nextSorter, {
      action,
      currentDataSource,
    });
  };

  const sorterState = useSorter(columns, (nextSorter) => {
    emitChange("sort", filtersRef.current, nextSorter);
  });
  sorterRef.current = sorterState.sorter;
  sortDataRef.current = sorterState.sortData;

  const filterState = useFilter(sorterState.columns, (nextFilters) => {
    emitChange("filter", nextFilters, sorterRef.current);
  });
  filtersRef.current = filterState.filters;
  filterDataRef.current = filterState.filterData;

  const processedData = sorterState.sortData(filterState.filterData(dataSource));

  const mergedPagination: false | TablePaginationConfig | undefined =
    pagination === false || pagination == null
      ? pagination
      : {
          ...pagination,
          onChange: (page, pageSize) => {
            pagination.onChange?.(page, pageSize);
            emitChange("paginate", filtersRef.current, sorterRef.current, {
              current: page,
              pageSize,
            });
          },
        };

  const { data, pagination: resolvedPagination } = usePagination(mergedPagination, processedData);
  paginationStateRef.current =
    resolvedPagination == null
      ? false
      : {
          current: resolvedPagination.page,
          pageSize: resolvedPagination.pageSize,
          total: resolvedPagination.total,
        };

  const { columns: mergedColumns, isSelected } = useSelection(
    rowSelection,
    data,
    filterState.columns,
    rowKey,
  );
  const layout = resolveTableLayout(mergedColumns, viewportWidth, scroll?.x);
  const widths = applyColumnWidthOverrides(layout.widths, columnWidths);
  const tableWidth = widths?.reduce((sum, width) => sum + width, 0) ?? layout.tableWidth;
  const scrollStyle: CSSProperties | undefined =
    scroll?.y == null ? undefined : { maxHeight: scroll.y };
  const tableStyle: CSSProperties =
    tableWidth != null
      ? { width: tableWidth }
      : scroll?.x === true
        ? { minWidth: "max-content" }
        : scroll?.x == null
          ? {}
          : { minWidth: scroll.x };
  const fixedRowHeight = rowHeight != null && rowHeight > 0 ? rowHeight : undefined;
  const stickyHeader = sticky || scroll?.y != null;
  const sizeClass = sizeClasses[size];

  const resize = useRef<{ index: number; startX: number; startWidths: number[] } | null>(null);
  const [resizing, setResizing] = useState(false);

  useEffect(() => {
    if (!resizing || !resize.current) return;
    const move = (event: PointerEvent) => {
      const state = resize.current;
      if (!state) return;
      const minWidth = pixelWidth(mergedColumns[state.index]?.minWidth) ?? MIN_RESIZE_WIDTH;
      const next = [...state.startWidths];
      next[state.index] = Math.max(
        minWidth,
        Math.round(state.startWidths[state.index] + event.clientX - state.startX),
      );
      onColumnWidthsChange?.(next);
    };
    const stop = () => {
      resize.current = null;
      setResizing(false);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop);
    return () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
    };
  }, [resizing, mergedColumns, onColumnWidthsChange]);

  const beginResize = (index: number, event: ReactPointerEvent<HTMLSpanElement>) => {
    if (!widths) return;
    event.preventDefault();
    resize.current = { index, startX: event.clientX, startWidths: [...widths] };
    setResizing(true);
  };

  const resetColumnWidth = (index: number) => {
    if (!columnWidths?.some((item) => item != null)) return;
    const next = mergedColumns.map((_, itemIndex) => columnWidths[itemIndex] ?? null);
    next[index] = null;
    onColumnWidthsChange?.(next);
  };

  return (
    <div className={cn("flex w-full min-w-0 flex-col", className)}>
      <div className="relative min-h-0 shrink-0">
        <div
          ref={viewportRef}
          className={cn(
            "min-h-0 w-full min-w-0 overflow-x-auto bg-[var(--yak-components-table-bg)]",
            scroll?.y != null && "overflow-y-auto",
          )}
          style={scrollStyle}
        >
          <table
            aria-busy={loading || undefined}
            className={cn(
              "text-[var(--yak-components-table-text)] tabular-nums",
              bordered ? "border-separate border-spacing-0" : "border-collapse",
              widths != null ? "table-fixed" : "w-full table-auto",
            )}
            style={tableStyle}
          >
            <colgroup>
              {mergedColumns.map((column, index) => (
                <col
                  key={getTableColumnKey(column, index)}
                  style={getColumnStyle(column, index, widths)}
                />
              ))}
            </colgroup>

            <thead>
              <tr>
                {mergedColumns.map((column, index) => {
                  const align = column.align ?? "left";
                  const fixed = column.fixed != null;

                  return (
                    <th
                      key={getTableColumnKey(column, index)}
                      scope="col"
                      style={getCellStyle(column, index, mergedColumns, widths)}
                      className={cn(
                        "group/th relative border-b border-[var(--yak-color-border)] font-medium text-[var(--yak-components-table-header-text)]",
                        stickyHeader
                          ? "bg-[var(--yak-components-panel-bg)]"
                          : "bg-[var(--yak-components-table-bg)]",
                        sizeClass.header,
                        alignClasses[align],
                        (stickyHeader || fixed) && "sticky",
                        stickyHeader && "top-0",
                        fixed ? "z-30" : stickyHeader && "z-20",
                        scroll?.x != null && fixedEdgeShadow(mergedColumns, index),
                        resizing && "select-none",
                      )}
                    >
                      <div className={cn("min-w-0", column.ellipsis && "truncate")}>
                        {column.title}
                      </div>
                      {enableColumnResize && !fixed && widths != null && (
                        <span
                          role="separator"
                          aria-orientation="vertical"
                          aria-label={`调整${typeof column.title === "string" ? column.title : ""}列宽，双击重置`}
                          tabIndex={0}
                          onPointerDown={(event) => beginResize(index, event)}
                          onDoubleClick={() => resetColumnWidth(index)}
                          onKeyDown={(event) => {
                            if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
                            event.preventDefault();
                            const minWidth = pixelWidth(column.minWidth) ?? MIN_RESIZE_WIDTH;
                            const current = widths[index];
                            const delta = event.key === "ArrowLeft" ? -16 : 16;
                            const next = [...widths];
                            next[index] = Math.max(minWidth, current + delta);
                            onColumnWidthsChange?.(next);
                          }}
                          className={cn(
                            "absolute inset-y-0 right-0 z-10 w-1.5 cursor-col-resize touch-none bg-[var(--yak-color-primary)] opacity-0 transition-opacity duration-150 group-hover/th:opacity-40 focus-visible:opacity-60 focus-visible:outline-none",
                            resizing && "opacity-100",
                          )}
                        />
                      )}
                    </th>
                  );
                })}
              </tr>
            </thead>

            <tbody>
              {data.map((record, rowIndex) => {
                const rowProps = onRow?.(record, rowIndex) ?? {};
                const { className: rowClassName, ...restRowProps } = rowProps;
                const selected = isSelected(record, rowIndex);

                return (
                  <tr
                    {...restRowProps}
                    key={resolveTableRowKey(record, rowIndex, rowKey)}
                    data-selected={selected ? "true" : undefined}
                    className={cn(
                      "bg-[var(--yak-components-table-row-bg)]",
                      "data-[selected=true]:bg-[var(--yak-color-surface-muted)]",
                      rowHoverable && "transition-colors hover:bg-[var(--yak-color-surface-muted)]",
                      "[&:last-child>td]:border-b-0",
                      rowClassName,
                    )}
                  >
                    {mergedColumns.map((column, columnIndex) => {
                      const value = column.dataIndex == null ? undefined : record[column.dataIndex];
                      const cell = column.render
                        ? column.render(value, record, rowIndex)
                        : (value as ReactNode);
                      const align = column.align ?? "left";
                      const fixed = column.fixed != null;

                      return (
                        <td
                          key={getTableColumnKey(column, columnIndex)}
                          style={{
                            ...getCellStyle(column, columnIndex, mergedColumns, widths),
                            ...(fixedRowHeight != null
                              ? { height: fixedRowHeight, paddingTop: 0, paddingBottom: 0 }
                              : {}),
                          }}
                          className={cn(
                            "border-b border-[var(--yak-components-table-border)] align-middle",
                            sizeClass.cell,
                            alignClasses[align],
                            bordered && "border-r first:border-l",
                            bordered && rowIndex === 0 && "border-t",
                            fixed && "sticky z-10 bg-inherit",
                            scroll?.x != null && fixedEdgeShadow(mergedColumns, columnIndex),
                          )}
                        >
                          <div
                            className={cn(
                              "min-w-0",
                              column.ellipsis && "truncate",
                              fixedRowHeight != null &&
                                "flex w-full flex-col justify-center overflow-hidden",
                            )}
                            style={
                              fixedRowHeight != null
                                ? { height: Math.max(0, fixedRowHeight - 1) }
                                : undefined
                            }
                            title={column.ellipsis ? getCellTitle(cell) : undefined}
                          >
                            {cell ?? null}
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                );
              })}

              {data.length === 0 && loading
                ? Array.from({ length: 3 }, (_, rowIndex) => (
                    <tr key={`skeleton-${rowIndex}`} aria-hidden="true">
                      {mergedColumns.map((column, columnIndex) => (
                        <td
                          key={getTableColumnKey(column, columnIndex)}
                          style={{
                            ...getCellStyle(column, columnIndex, mergedColumns, layout.widths),
                            height: fixedRowHeight ?? 56,
                          }}
                          className={cn(
                            "border-b border-[var(--yak-components-table-border)] px-3",
                            bordered && "border-r first:border-l",
                            bordered && rowIndex === 0 && "border-t",
                            column.fixed != null &&
                              "sticky z-10 bg-[var(--yak-components-table-bg)]",
                          )}
                        >
                          <Skeleton
                            width={columnIndex === mergedColumns.length - 1 ? "65%" : "80%"}
                            height={12}
                          />
                        </td>
                      ))}
                    </tr>
                  ))
                : null}
              {data.length === 0 && !loading ? (
                <tr>
                  <td
                    colSpan={Math.max(mergedColumns.length, 1)}
                    className={cn(
                      "border-b border-[var(--yak-components-table-border)]",
                      bordered && "border-x border-t",
                    )}
                  >
                    <Empty className="min-h-48" description={emptyText ?? "No data"} />
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      {footer || resolvedPagination ? (
        <div className="mt-3 flex min-h-12 shrink-0 flex-nowrap items-center gap-4 px-4">
          {footer ? <div className="min-w-0 flex-1">{footer}</div> : null}
          {resolvedPagination ? (
            <Pagination
              className="ml-auto shrink-0"
              page={resolvedPagination.page}
              pageSize={resolvedPagination.pageSize}
              total={resolvedPagination.total}
              pageSizeOptions={resolvedPagination.pageSizeOptions}
              pageSizeLabel={resolvedPagination.pageSizeLabel}
              disabled={loading || resolvedPagination.disabled}
              showSizeChanger={resolvedPagination.showSizeChanger}
              showQuickJumper={resolvedPagination.showQuickJumper}
              renderTotal={resolvedPagination.renderTotal}
              onChange={resolvedPagination.onChange}
            />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
