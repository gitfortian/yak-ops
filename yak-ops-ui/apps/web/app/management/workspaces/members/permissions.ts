import type { WorkspaceMemberRecord, WorkspaceRole } from "@/service/workspace";

export const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: "所有者",
  ADMIN: "管理员",
  MEMBER: "成员",
};

export type MemberRow = WorkspaceMemberRecord & {
  userName?: string;
  realName?: string | null;
};

export function canChangeMember(actorRole: WorkspaceRole, member: WorkspaceMemberRecord, ownerCount: number) {
  const canManage = actorRole === "OWNER" || actorRole === "ADMIN";
  return canManage && (actorRole === "OWNER" || member.role !== "OWNER") && !(member.role === "OWNER" && ownerCount <= 1);
}

export function allowedMemberRoles(actorRole: WorkspaceRole): WorkspaceRole[] {
  return actorRole === "OWNER" ? ["OWNER", "ADMIN", "MEMBER"] : ["ADMIN", "MEMBER"];
}
