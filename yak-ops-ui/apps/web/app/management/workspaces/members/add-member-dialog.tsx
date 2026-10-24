import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, Table, type TableColumns
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { searchUsers, type UserBriefRecord } from "@/service/user";
import { addWorkspaceMember, type WorkspaceRole } from "@/service/workspace";
import { ROLE_LABELS, allowedMemberRoles } from "./permissions";

interface Props {
  workspaceId: string;
  existingUserIds: string[];
  canAssignOwner: boolean;
  onClose: () => void;
  onAdded: () => void;
}
const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export function AddMemberDialog({ workspaceId, existingUserIds, canAssignOwner, onClose, onAdded }: Props) {
  const { currentUser } = useAuth();
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [role, setRole] = useState<WorkspaceRole>("MEMBER");
  const existingSet = useMemo(() => new Set(existingUserIds), [existingUserIds]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword.trim()), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const users = useQuery({
    queryKey: ["management", "member-user-search", currentUser?.id ?? "", workspaceId, debouncedKeyword],
    enabled: Boolean(currentUser?.id && workspaceId && debouncedKeyword),
    queryFn: () => searchUsers(debouncedKeyword),
  });
  const records = (debouncedKeyword && keyword.trim() === debouncedKeyword ? (users.data ?? []) : [])
    .filter(user => !existingSet.has(user.id));
  const add = useMutation({
    mutationFn: (userId: string) => addWorkspaceMember(workspaceId, { userId, role }),
    onSuccess: () => {
      onAdded();
      onClose();
    },
  });
  const columns: TableColumns<UserBriefRecord> = [
    {
      key: "user",
      title: "用户",
      minWidth: 260,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">{record.realName || record.userName}</div>
          <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">{record.userName}</div>
        </div>
      ),
    },
    { key: "email", title: "邮箱", dataIndex: "email", minWidth: 220, ellipsis: true, render: (_value, record) => record.email || "-" },
    {
      key: "actions", title: "操作", width: 90, align: "center",
      render: (_value, record) => (
        <Button size="small" variant="primary" loading={add.isPending && add.variables === record.id}
          disabled={add.isPending} onClick={() => add.mutate(record.id)}>添加</Button>
      ),
    },
  ];
  return (
    <Modal open width={760} bodyClassName="py-3" title="添加成员" onClose={() => { if (!add.isPending) onClose(); }}>
      <div className="space-y-3">
        <div className="space-y-2.5">
          <Field className={HORIZONTAL_FIELD_CLASS}>
            <FieldLabel htmlFor="workspace-member-keyword" className="pt-1.5 text-xs leading-4">搜索用户</FieldLabel>
            <Input id="workspace-member-keyword" size="small" variant="outlined" value={keyword}
              placeholder="输入用户名或姓名" onChange={event => setKeyword(event.target.value)} />
          </Field>
          <Field className={HORIZONTAL_FIELD_CLASS}>
            <FieldLabel className="pt-1.5 text-xs leading-4">加入角色</FieldLabel>
            <Select<WorkspaceRole> size="small" items={ROLE_LABELS} value={role}
              onValueChange={nextRole => { if (nextRole) setRole(nextRole); }}>
              <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
              <SelectContent>
                {allowedMemberRoles(canAssignOwner ? "OWNER" : "ADMIN").map(option => (
                  <SelectItem key={option} value={option}><SelectItemText>{ROLE_LABELS[option]}</SelectItemText><SelectItemIndicator /></SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>
        {users.isError && keyword.trim() === debouncedKeyword ? (
          <div role="alert" className="flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]">
            用户搜索失败 <Button size="small" onClick={() => void users.refetch()}>重试</Button>
          </div>
        ) : null}
        <Table<UserBriefRecord> columns={columns} dataSource={records} rowKey="id"
          loading={Boolean(keyword.trim()) && (users.isFetching || keyword.trim() !== debouncedKeyword)}
          bordered size="medium" scroll={{ x: 620 }}
          emptyText={keyword.trim() ? "未找到可添加的用户" : "请输入用户名或姓名搜索"} pagination={false} />
      </div>
    </Modal>
  );
}
