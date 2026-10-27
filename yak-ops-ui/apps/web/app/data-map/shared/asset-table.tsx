import { Badge, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import { locationLabel, TABLE_STATUS_LABEL, TABLE_STATUS_TONE, tablePath } from "./labels";
import { useDomainTaxonomy } from "./lookups";

interface Props {
  records: TableSummary[];
  loading: boolean;
  emptyText?: ReactNode;
  pagination?:
    | false
    | {
        pageNo: number;
        pageSize: number;
        total: number;
        onPageChange: (page: number, size: number) => void;
      };
}

const MAX_VISIBLE_TAGS = 2;

const TagList = ({ names }: { names: string[] }) => {
  if (names.length === 0) return <span className="text-[var(--yak-components-muted-text)]">-</span>;
  const visible = names.slice(0, MAX_VISIBLE_TAGS);
  return (
    <span className="flex min-w-0 flex-wrap items-center gap-1">
      {visible.map((name) => (
        <Badge key={name} tone="info">
          {name}
        </Badge>
      ))}
      {names.length > visible.length && (
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          +{names.length - visible.length}
        </span>
      )}
    </span>
  );
};

/** Asset list shared by the catalog result grid and the personal favourite/recent grids. */
export default function AssetTable({
  records,
  loading,
  emptyText = "暂无数据",
  pagination = false,
}: Props) {
  const { domainNames } = useDomainTaxonomy();

  const columns: TableColumns<TableSummary> = [
    {
      key: "name",
      title: "资产",
      minWidth: 260,
      render: (_value, record) => (
        <div className="min-w-0">
          <Link
            to={tablePath(record.id)}
            className="block truncate text-[13px] font-medium [color:var(--yak-color-primary)] hover:underline"
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
      key: "description",
      title: "描述",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="line-clamp-2 text-[13px]" title={record.description ?? undefined}>
            {record.description || record.remarks || "-"}
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
      key: "datasource",
      title: "数据源",
      dataIndex: "datasourceName",
      width: 160,
      ellipsis: true,
      render: (_value, record) => record.datasourceName || record.datasourceId,
    },
    {
      key: "domain",
      title: "数据域",
      width: 140,
      ellipsis: true,
      render: (_value, record) => {
        const name = record.domainId ? domainNames.get(record.domainId) : undefined;
        return name ?? <span className="text-[var(--yak-components-muted-text)]">未分类</span>;
      },
    },
    {
      key: "tags",
      title: "标签",
      width: 170,
      render: (_value, record) => <TagList names={record.tags.map((tag) => tag.name)} />,
    },
    {
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) => (
        <Badge tone={TABLE_STATUS_TONE[record.status]}>{TABLE_STATUS_LABEL[record.status]}</Badge>
      ),
    },
    {
      key: "lastSeen",
      title: "最近采集",
      dataIndex: "lastSeenTime",
      width: 170,
      render: (_value, record) => record.lastSeenTime || "-",
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={records}
      rowKey="id"
      size="medium"
      loading={loading}
      emptyText={emptyText}
      rowHoverable
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
      scroll={{ x: 1180 }}
    />
  );
}
