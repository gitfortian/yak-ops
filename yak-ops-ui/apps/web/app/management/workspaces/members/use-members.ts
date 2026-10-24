import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/hooks/use-auth";
import { getUsersByIds, type UserRecord } from "@/service/user";
import { getWorkspaceMembers } from "@/service/workspace";
import type { MemberRow } from "./permissions";

export const memberQueryKeys = {
  all: ["management", "members"] as const,
  forWorkspace: (userId: string, workspaceId: string) => [...memberQueryKeys.all, userId, workspaceId] as const,
};

export function useMembers(workspaceId: string) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  return useQuery({
    queryKey: memberQueryKeys.forWorkspace(userId, workspaceId),
    enabled: Boolean(userId && workspaceId),
    queryFn: async (): Promise<MemberRow[]> => {
      const members = await getWorkspaceMembers(workspaceId);
      const userIds = [...new Set(members.map(member => member.userId).filter(Boolean))];
      const users = await getUsersByIds(userIds);
      const byId = new Map<string, UserRecord>(users.map(user => [user.id, user]));
      return members.map(member => ({
        ...member,
        userName: byId.get(member.userId)?.userName,
        realName: byId.get(member.userId)?.realName,
      }));
    },
  });
}
