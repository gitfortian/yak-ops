import { Table, type TableColumns } from "@yak-ops/yak-ui";
import { Link } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import { locationLabel, relativeTime, tablePath } from "../shared/labels";
import { useDomainTaxonomy, useOwnerNames } from "../shared/lookups";

/** The most viewed assets of the Workspace, ranked by summed visit counts. */
export default function HotTables({
  records,
  loading,
}: {
  records: TableSummary[];
  loading: boolean;
}) {
  const { domainNames } = useDomainTaxonomy();
  const { names: ownerNames } = useOwnerNames();

  const columns: TableColumns<TableSummary> = [
    {
      key: "rank",
      title: "#",
      width: 48,
      render: (_value, record) => (
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          {records.indexOf(record) + 1}
        </span>
      ),
    },
    {
      key: "name",
      title: "资产",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <Link
            to={tablePath(record.id)}
            className="block truncate text-sm font-medium [color:var(--yak-color-primary)] hover:underline"
            title={record.name}
          >
            {record.name}
          </Link>
          <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
            {locationLabel(record.database, record.schema)}
          </div>
        </div>
      ),
    },
    {
      key: "domain",
      title: "数据域",
      width: 120,
      ellipsis: true,
      render: (_value, record) =>
        (record.domainId ? domainNames.get(record.domainId) : undefined) ?? "未分类",
    },
    {
      key: "owner",
      title: "负责人",
      width: 110,
      ellipsis: true,
      render: (_value, record) =>
        (record.ownerId ? ownerNames.get(record.ownerId) : undefined) ?? (
          <span className="text-[var(--yak-components-muted-text)]">未指派</span>
        ),
    },
    {
      key: "tags",
      title: "标签",
      width: 150,
      render: (_value, record) =>
        record.tags.length > 0 ? (
          <span className="flex min-w-0 flex-wrap gap-1">
            {record.tags.slice(0, 2).map((tag) => (
              <span
                key={tag.valueId}
                className="inline-flex items-center rounded-full bg-[var(--yak-components-badge-info-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--yak-components-badge-info-text)]"
              >
                {tag.valueName}
              </span>
            ))}
          </span>
        ) : (
          <span className="text-[var(--yak-components-muted-text)]">-</span>
        ),
    },
    {
      key: "lastSeen",
      title: "最近访问",
      width: 110,
      render: (_value, record) => (
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          {relativeTime(record.lastSeenTime)}
        </span>
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={records}
      rowKey="id"
      size="small"
      loading={loading}
      emptyText="暂无访问记录"
      pagination={false}
      scroll={{ x: 760 }}
    />
  );
}
