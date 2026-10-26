import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Modal,
  toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  removeWorkspaceMember,
  updateWorkspaceMemberRole,
  type WorkspaceRecord,
  type WorkspaceRole,
} from "@/service/workspace";
import { workspaceQueryKeys } from "../use-workspaces";
import { useMembers, memberQueryKeys } from "./use-members";
import { canChangeMember, type MemberRow } from "./permissions";
import { MemberTable } from "./member-table";
import { AddMemberDialog } from "./add-member-dialog";

interface Props {
  workspace: WorkspaceRecord;
  onClose: () => void;
  onRefresh: () => void | Promise<void>;
}

export function MemberDialog({ workspace, onClose, onRefresh }: Props) {
  const { currentUser } = useAuth();
  const { refreshWorkspaces } = useWorkspace();
  const queryClient = useQueryClient();
  const membersQuery = useMembers(workspace.id);
  const members = membersQuery.data ?? [];
  const [adding, setAdding] = useState(false);
  const [pendingRemove, setPendingRemove] = useState<MemberRow>();
  const actorRole = workspace.role;
  const canManage = actorRole === "OWNER" || actorRole === "ADMIN";
  const ownerCount = members.filter((member) => member.role === "OWNER").length;
  const refreshMembers = () => queryClient.invalidateQueries({ queryKey: memberQueryKeys.all });
  const refreshLists = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.all }),
      refreshWorkspaces(),
    ]);
    await onRefresh();
  };

  const updateRole = useMutation({
    mutationFn: ({ member, role }: { member: MemberRow; role: WorkspaceRole }) =>
      updateWorkspaceMemberRole(workspace.id, member.userId, { role }),
    onSuccess: async (_updated, { member }) => {
      toast.success("成员角色更新成功");
      if (member.userId === currentUser?.id) await refreshLists();
      await refreshMembers();
    },
  });
  const remove = useMutation({
    mutationFn: (member: MemberRow) => removeWorkspaceMember(workspace.id, member.userId),
    onSuccess: async (_result, member) => {
      toast.success("成员移除成功");
      setPendingRemove(undefined);
      if (member.userId === currentUser?.id) {
        onClose();
        await refreshLists();
      } else {
        await refreshMembers();
      }
    },
  });
  const busyUserId = updateRole.isPending
    ? (updateRole.variables?.member.userId ?? "")
    : remove.isPending
      ? (remove.variables?.userId ?? "")
      : "";
  const close = () => {
    if (!updateRole.isPending && !remove.isPending) onClose();
  };
  return (
    <>
      <Modal open width={820} title={workspace.name + " · 成员"} onClose={close}>
        <div className="space-y-3">
          {canManage ? (
            <div className="flex justify-end">
              <Button size="small" variant="primary" onClick={() => setAdding(true)}>
                <Plus size={14} />
                添加成员
              </Button>
            </div>
          ) : null}
          {membersQuery.isError ? (
            <div
              role="alert"
              className="flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
            >
              成员加载失败{" "}
              <Button size="small" onClick={() => void membersQuery.refetch()}>
                重试
              </Button>
            </div>
          ) : null}
          <MemberTable
            members={members}
            loading={membersQuery.isPending || membersQuery.isFetching}
            actorRole={actorRole}
            busyUserId={busyUserId}
            onRoleChange={(member, role) => {
              if (
                !updateRole.isPending &&
                !remove.isPending &&
                canChangeMember(actorRole, member, ownerCount)
              ) {
                updateRole.mutate({ member, role });
              }
            }}
            onRemove={(member) => setPendingRemove(member)}
          />
        </div>
      </Modal>
      {adding && canManage ? (
        <AddMemberDialog
          workspaceId={workspace.id}
          existingUserIds={members.map((member) => member.userId)}
          canAssignOwner={actorRole === "OWNER"}
          onClose={() => setAdding(false)}
          onAdded={() => {
            toast.success("成员添加成功");
            void refreshMembers();
          }}
        />
      ) : null}
      <Dialog
        open={Boolean(pendingRemove)}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) setPendingRemove(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">移除成员</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定将“
            {pendingRemove?.realName || pendingRemove?.userName || pendingRemove?.userId || "-"}
            ”移出当前工作空间吗？
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="small"
              disabled={remove.isPending}
              onClick={() => setPendingRemove(undefined)}
            >
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={remove.isPending}
              onClick={() => {
                if (pendingRemove && canChangeMember(actorRole, pendingRemove, ownerCount))
                  remove.mutate(pendingRemove);
              }}
            >
              移除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
