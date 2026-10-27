import { Badge, Table, type TableColumns } from "@yak-ops/yak-ui";

import type { TableColumn } from "@/service/data-map/types";

const columns: TableColumns<TableColumn> = [
  {
    key: "name",
    title: "字段",
    minWidth: 200,
    render: (_value, record) => (
      <span className="flex items-center gap-2">
        <span className="truncate text-[13px] font-medium">{record.name}</span>
        {record.primaryKey && <Badge tone="info">PK</Badge>}
      </span>
    ),
  },
  {
    key: "type",
    title: "类型",
    width: 180,
    render: (_value, record) => {
      const size =
        record.size === null
          ? ""
          : `(${record.size}${record.scale === null ? "" : `,${record.scale}`})`;
      return (
        <span className="text-[13px]">
          {record.typeName}
          {size}
        </span>
      );
    },
  },
  {
    key: "nullable",
    title: "可空",
    width: 80,
    render: (_value, record) => (record.nullable ? "是" : "否"),
  },
  {
    key: "ordinal",
    title: "序号",
    dataIndex: "ordinal",
    width: 80,
    align: "right",
  },
  {
    key: "remarks",
    title: "注释",
    dataIndex: "remarks",
    minWidth: 220,
    ellipsis: true,
    render: (_value, record) => record.remarks || "-",
  },
];

export default function ColumnsTab({ columns: records }: { columns: TableColumn[] }) {
  return (
    <Table
      columns={columns}
      dataSource={records}
      rowKey="ordinal"
      size="medium"
      pagination={false}
      scroll={{ x: 900 }}
    />
  );
}
