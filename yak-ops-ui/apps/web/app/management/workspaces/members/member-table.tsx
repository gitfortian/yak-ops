import { Button, Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { WorkspaceRole } from "@/service/workspace";
import { ROLE_LABELS, allowedMemberRoles, canChangeMember, type MemberRow } from "./permissions";

interface Props {
  members: MemberRow[];
  loading: boolean;
  actorRole: WorkspaceRole;
  busyUserId: string;
  onRoleChange: (member: MemberRow, role: WorkspaceRole) => void;
  onRemove: (member: MemberRow) => void;
}

export function MemberTable({ members, loading, actorRole, busyUserId, onRoleChange, onRemove }: Props) {
  const ownerCount = members.filter(member => member.role === "OWNER").length;
    const columns: TableColumns<MemberRow> = [
    {
      key: "user",
      title: "成员",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
            {record.realName || record.userName || record.userId}
          </div>
          <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
            {record.userName || record.userId}
          </div>
        </div>
      ),
    },
    {
      key: "role",
      title: "角色",
      width: 180,
      render: (_value, record) => {
        const canEditTarget = canChangeMember(actorRole, record, ownerCount);

        if (!canEditTarget) return record.roleName || ROLE_LABELS[record.role];

        const roleOptions = allowedMemberRoles(actorRole);

        return (
          <Select<WorkspaceRole>
            size="small"
            items={ROLE_LABELS}
            value={record.role}
            disabled={busyUserId === record.userId}
            onValueChange={(role) => {
              if (role) void onRoleChange(record, role);
            }}
          >
            <SelectTrigger variant="outlined" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {roleOptions.map((role) => (
                <SelectItem key={role} value={role}>
                  <SelectItemText>{ROLE_LABELS[role]}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );
      },
    },
    {
      key: "joinedAt",
      title: "加入时间",
      dataIndex: "joinedAt",
      width: 180,
      render: (_value, record) => record.joinedAt || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 90,
      align: "center",
      render: (_value, record) => {
        const canRemove = canChangeMember(actorRole, record, ownerCount);

        if (!canRemove) return "-";

        return (
          <Button
            variant="ghost"
            size="small"
            className="px-0.5 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-danger)]"
            onClick={() => onRemove(record)}
          >
            移除
          </Button>
        );
      },
    },
  ];
  return (
    <Table<MemberRow>
      columns={columns}
      dataSource={members}
      rowKey="userId"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 680 }}
      emptyText="暂无成员"
      pagination={false}
    />
  );
}
