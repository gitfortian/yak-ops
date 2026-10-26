import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { listWorkspaces } from "@/service/workspace";

export const workspaceQueryKeys = {
  all: ["management", "workspaces"] as const,
  list: (userId: string) => [...workspaceQueryKeys.all, userId] as const,
};

export function useWorkspaces() {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  return useQuery({
    queryKey: workspaceQueryKeys.list(userId),
    queryFn: listWorkspaces,
    enabled: Boolean(userId),
  });
}
