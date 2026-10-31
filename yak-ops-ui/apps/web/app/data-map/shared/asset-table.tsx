import { Badge, Table, type TableColumns } from "@yak-ops/yak-ui";
import { Table2 } from "lucide-react";
import type { ReactNode } from "react";

import type { TableSummary } from "@/service/data-map/types";

import { relativeTime, TABLE_STATUS_LABEL, TABLE_STATUS_TONE } from "./labels";
import { useDomainTaxonomy } from "./lookups";
import DomainBadge from "./domain-badge";
import OwnerCell, { AssetNameCell } from "./cells";

interface Props {
  records: TableSummary[];
  loading: boolean;
  emptyText?: ReactNode;
  /** Leading star column for lists that own the favourite state, such as 我的数据-收藏. */
  favoriteAction?: (record: TableSummary) => ReactNode;
  /** Caller-held column width overrides, persisted per browser. */
  columnWidths?: (number | null)[];
  onColumnWidthsChange?: (columnWidths: (number | null)[]) => void;
  pagination?:
    | false
    | {
        pageNo: number;
        pageSize: number;
        total: number;
        onPageChange: (page: number, size: number) => void;
      };
}

const MAX_VISIBLE_TAGS = 3;

function TagList({ tags }: { tags: TableSummary["tags"] }) {
  if (tags.length === 0) return <span className="text-[var(--yak-components-muted-text)]">-</span>;
  const visible = tags.slice(0, MAX_VISIBLE_TAGS);
  return (
    <span className="flex min-w-0 flex-wrap items-center gap-1">
      {visible.map((tag) => (
        <span
          key={tag.valueId}
          className="inline-flex items-center whitespace-nowrap rounded-[4px] border border-transparent bg-[var(--yak-components-collapse-section-bg)] px-[7px] py-0.5 text-[11px] font-medium text-[var(--yak-components-panel-text)]"
        >
          {tag.valueName}
        </span>
      ))}
      {tags.length > visible.length && (
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          +{tags.length - visible.length}
        </span>
      )}
    </span>
  );
}

/** Asset list shared by the catalog result grid and the personal favourite/recent grids. */
export default function AssetTable({
  records,
  loading,
  emptyText = "暂无数据",
  favoriteAction,
  columnWidths,
  onColumnWidthsChange,
  pagination = false,
}: Props) {
  const { domainNames } = useDomainTaxonomy();

  const columns: TableColumns<TableSummary> = [
    ...(favoriteAction
      ? [
          {
            key: "favorite",
            title: "",
            width: 44,
            render: (_value: unknown, record: TableSummary) => favoriteAction(record),
          },
        ]
      : []),
    {
      key: "name",
      title: "资产",
      minWidth: 240,
      render: (_value, record) => (
        <div className="flex min-w-0 items-start gap-2">
          <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
            <Table2 size={13} aria-hidden="true" />
          </span>
          <AssetNameCell record={record} mono />
        </div>
      ),
    },
    {
      key: "description",
      title: "描述",
      minWidth: 200,
      render: (_value, record) => (
        <div className="min-w-0">
          <div
            className="line-clamp-2 text-[13px] leading-5 text-[var(--yak-components-panel-text)]"
            title={record.description ?? undefined}
          >
            {record.description || record.remarks || (
              <span className="text-[var(--yak-components-muted-text)]">— 缺少描述 —</span>
            )}
          </div>
          {record.matchedColumns.length > 0 && (
            <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
              匹配字段：{record.matchedColumns.join("、")}
            </div>
          )}
        </div>
      ),
    },
    {
      key: "domain",
      title: "数据域",
      width: 110,
      render: (_value, record) => (
        <DomainBadge
          id={record.domainId}
          name={record.domainId ? domainNames.get(record.domainId) : undefined}
        />
      ),
    },
    {
      key: "owner",
      title: "负责人",
      width: 120,
      render: (_value, record) => <OwnerCell ownerId={record.ownerId} />,
    },
    {
      key: "tags",
      title: "标签",
      width: 150,
      render: (_value, record) => <TagList tags={record.tags} />,
    },
    {
      key: "status",
      title: "状态",
      width: 88,
      render: (_value, record) => (
        <Badge tone={TABLE_STATUS_TONE[record.status]}>{TABLE_STATUS_LABEL[record.status]}</Badge>
      ),
    },
    {
      key: "lastSeen",
      title: "最近采集",
      width: 100,
      render: (_value, record) => (
        <span
          className="text-xs text-[var(--yak-components-muted-text)] [font-variant-numeric:tabular-nums]"
          title={record.lastSeenTime}
        >
          {relativeTime(record.lastSeenTime)}
        </span>
      ),
    },
  ];

  const stale = loading && records.length > 0;

  return (
    <div
      className={`transition-opacity duration-150 ${stale ? "opacity-60" : "opacity-100"}`}
      aria-busy={stale || undefined}
    >
      <Table
        columns={columns}
        dataSource={records}
        rowKey="id"
        size="medium"
        loading={loading && records.length === 0}
        emptyText={emptyText}
        rowHoverable
        enableColumnResize
        columnWidths={columnWidths}
        onColumnWidthsChange={onColumnWidthsChange}
        pagination={
          pagination
            ? {
                current: pagination.pageNo,
                pageSize: pagination.pageSize,
                total: pagination.total,
                showSizeChanger: true,
                onChange: pagination.onPageChange,
              }
            : false
        }
        scroll={{ x: 1010 }}
      />
    </div>
  );
}
