import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import {
  SECURITY_LABEL,
  type StandardField,
  type StandardPage,
} from "@/service/data-modeling/data-standards/types";
interface Props {
  page: StandardPage | null;
  pageNo: number;
  pageSize: number;
  loading: boolean;
  searching: boolean;
  onEdit: (row: StandardField) => void;
  onDelete: (row: StandardField) => void;
  onPageChange: (page: number, size: number) => void;
}
export default function StandardTable(props: Props) {
  const columns: TableColumns<StandardField> = [
    { title: "中文名称", dataIndex: "cnName", width: 180, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 180, ellipsis: true },
    { title: "数据类型", dataIndex: "dataType", width: 120 },
    {
      title: "长度/精度",
      key: "precision",
      width: 110,
      render: (_, row) => row.lengthPrecision ?? "—",
    },
    {
      title: "业务预设值",
      key: "default",
      width: 140,
      ellipsis: true,
      render: (_, row) => row.defaultValue ?? "—",
    },
    {
      title: "关联标准",
      key: "links",
      width: 170,
      ellipsis: true,
      render: (_, row) =>
        [row.lookupCnName, row.measureUnitCnName, row.parentStandardEnCode]
          .filter(Boolean)
          .join(" / ") || "—",
    },
    {
      title: "安全等级",
      key: "security",
      width: 100,
      render: (_, row) => SECURITY_LABEL[row.securityLevel],
    },
    {
      title: "负责人",
      key: "owner",
      width: 140,
      render: (_, row) => (
        <span>
          {row.ownerName ?? row.ownerId}
          {!row.ownerActive && "（已不可选）"}
        </span>
      ),
    },
    { title: "更新时间", dataIndex: "updateTime", width: 180 },
  ];
  if (props.searching)
    columns.push({ title: "所属分类", dataIndex: "categoryPath", width: 220, ellipsis: true });
  columns.push({
    title: "操作",
    key: "actions",
    width: 120,
    fixed: "right",
    align: "center",
    render: (_, row) => (
      <div className="flex items-center justify-center gap-2">
        <Button
          size="small"
          variant="ghost"
          disabled={props.loading}
          className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
          onClick={() => props.onEdit(row)}
        >
          编辑
        </Button>
        <Button
          size="small"
          variant="ghost"
          disabled={props.loading}
          className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
          onClick={() => props.onDelete(row)}
        >
          删除
        </Button>
      </div>
    ),
  });
  return (
    <Table<StandardField>
      rowKey="id"
      columns={columns}
      dataSource={props.page?.bizData ?? []}
      loading={props.loading}
      size="small"
      bordered={false}
      scroll={{ x: props.searching ? 1780 : 1560 }}
      emptyText="暂无字段标准"
      pagination={{
        current: props.pageNo,
        pageSize: props.pageSize,
        total: props.page?.pagination.total ?? 0,
        disabled: props.loading,
        showSizeChanger: true,
        pageSizeOptions: [20, 50, 100],
        onChange: props.onPageChange,
      }}
    />
  );
}
