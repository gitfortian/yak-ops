import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { WorkspaceRecord } from "@/service/workspace";

interface Props {
  records: WorkspaceRecord[];
  loading: boolean;
  onMembers: (workspace: WorkspaceRecord) => void;
}

export function WorkspaceTable({ records, loading, onMembers }: Props) {
  const columns: TableColumns<WorkspaceRecord> = [
    {
      key: "name",
      title: "工作空间名称",
      dataIndex: "name",
      minWidth: 220,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.name || "-"}
        </span>
      ),
    },
    {
      key: "description",
      title: "描述",
      dataIndex: "description",
      minWidth: 260,
      ellipsis: true,
      render: (_value, record) => record.description || "-",
    },
    {
      key: "role",
      title: "我的角色",
      width: 140,
      render: (_value, record) => record.roleName || record.role || "-",
    },
    {
      key: "createTime",
      title: "创建时间",
      dataIndex: "createTime",
      width: 170,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "updateTime",
      title: "修改时间",
      dataIndex: "updateTime",
      width: 170,
      render: (_value, record) => record.updateTime || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 110,
      align: "center",
      render: (_value, record) => (
        <Button
          variant="ghost"
          size="small"
          className="px-0.5 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
          onClick={() => void onMembers(record)}
        >
          {record.role === "MEMBER" ? "查看成员" : "管理成员"}
        </Button>
      ),
    },
  ];
  return (
    <Table<WorkspaceRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 1080 }}
      emptyText="暂无工作空间"
      pagination={false}
    />
  );
}
