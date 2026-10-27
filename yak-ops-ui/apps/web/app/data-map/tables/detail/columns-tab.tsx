import { Empty, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { Check, Minus, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import type { TableColumn } from "@/service/data-map/types";

import { columnTypeFamily, formatColumnType, type ColumnTypeFamily } from "./column-type";

/** Chip colours mirror the field list's families; the tokens carry both themes. */
const TYPE_CHIP: Record<ColumnTypeFamily, string> = {
  character:
    "bg-[var(--yak-datamap-type-character-bg)] text-[var(--yak-datamap-type-character-text)]",
  numeric: "bg-[var(--yak-datamap-type-numeric-bg)] text-[var(--yak-datamap-type-numeric-text)]",
  temporal: "bg-[var(--yak-datamap-type-temporal-bg)] text-[var(--yak-datamap-type-temporal-text)]",
  other: "bg-[var(--yak-datamap-type-other-bg)] text-[var(--yak-datamap-type-other-text)]",
};

/** Typing pauses cost nothing: the filter runs on already-loaded rows, 500ms after the last key. */
const SEARCH_DEBOUNCE_MS = 500;

/** Keeps rows with a missing ordinal at the end instead of shuffling them to the top. */
const ordinalOf = (column: TableColumn) =>
  typeof column.ordinal === "number" && Number.isFinite(column.ordinal)
    ? column.ordinal
    : Number.MAX_SAFE_INTEGER;

const columns: TableColumns<TableColumn> = [
  {
    key: "name",
    title: "字段",
    minWidth: 220,
    render: (_value, record) => (
      <span className="flex min-w-0 items-center gap-2">
        <span className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
          {record.name}
        </span>
        {record.primaryKey === true && (
          <span className="shrink-0 rounded-[3px] bg-[var(--yak-datamap-type-numeric-bg)] px-1 text-[10px] font-semibold leading-4 text-[var(--yak-datamap-type-numeric-text)] ring-1 ring-[var(--yak-datamap-type-numeric-text)] ring-inset">
            PK
          </span>
        )}
      </span>
    ),
  },
  {
    key: "type",
    title: "类型",
    width: 190,
    render: (_value, record) => (
      <span
        className={`inline-flex items-center rounded px-1.5 py-0.5 font-mono text-[11px] font-medium ${TYPE_CHIP[columnTypeFamily(record.typeName)]}`}
      >
        {formatColumnType(record)}
      </span>
    ),
  },
  {
    key: "nullable",
    title: "可空",
    width: 72,
    align: "center",
    render: (_value, record) =>
      record.nullable ? (
        <Check
          size={14}
          aria-label="可空"
          className="mx-auto [color:var(--yak-datamap-type-character-text)]"
        />
      ) : (
        <Minus
          size={14}
          aria-label="非空"
          className="mx-auto text-[var(--yak-components-muted-text)] opacity-45"
        />
      ),
  },
  {
    key: "remarks",
    title: "注释",
    minWidth: 220,
    ellipsis: true,
    render: (_value, record) => (
      <span
        className="block truncate text-[13px] text-[var(--yak-components-muted-text)]"
        title={record.remarks ?? undefined}
      >
        {record.remarks || "-"}
      </span>
    ),
  },
  {
    key: "ordinal",
    title: "序号",
    width: 64,
    align: "right",
    render: (_value, record) => (
      <span className="text-xs text-[var(--yak-components-muted-text)] opacity-60">
        {record.ordinal ?? "-"}
      </span>
    ),
  },
];

/**
 * Lists every column of one asset.
 *
 * <p>The rows are ordered by ordinal position here as well as in the query, so a stale or
 * hand-edited response can never present the fields out of sequence. Nothing is paginated or
 * truncated: a seventeen-column table renders seventeen rows. The search filters the loaded rows
 * in memory, because a single table never has enough columns to justify a round trip.
 */
export default function ColumnsTab({ columns: records }: { columns: TableColumn[] }) {
  const [keyword, setKeyword] = useState("");
  const [committed, setCommitted] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(
      () => setCommitted(keyword.trim().toLowerCase()),
      SEARCH_DEBOUNCE_MS,
    );
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const ordered = useMemo(
    () => [...records].sort((left, right) => ordinalOf(left) - ordinalOf(right)),
    [records],
  );

  const visible = useMemo(() => {
    if (committed.length === 0) return ordered;
    return ordered.filter(
      (column) =>
        column.name.toLowerCase().includes(committed) ||
        (column.remarks ?? "").toLowerCase().includes(committed),
    );
  }, [ordered, committed]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          共 {records.length} 个字段
          {committed.length > 0 && ` · 匹配 ${visible.length} 个`}
        </span>
        <div className="relative w-[260px] max-md:w-full">
          <Search
            size={13}
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            className="pl-8"
            placeholder="搜索字段名或注释"
            aria-label="搜索字段"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
      </div>

      {visible.length === 0 ? (
        <Empty
          className="min-h-32 rounded border border-dashed border-[var(--yak-components-panel-border)]"
          description={`没有匹配「${committed}」的字段`}
        />
      ) : (
        <Table
          columns={columns}
          dataSource={visible}
          rowKey={(record) => `${record.ordinal ?? ""}:${record.name}`}
          size="medium"
          pagination={false}
          scroll={{ x: 900 }}
        />
      )}
    </div>
  );
}
