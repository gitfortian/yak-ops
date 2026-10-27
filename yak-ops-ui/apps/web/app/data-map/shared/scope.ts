import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";

/** User and Workspace identity that every data map query key must carry. */
export type DataMapScope = readonly [userId: string, workspaceId: string];

export function useDataMapScope() {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const userId = currentUser?.id ?? "";
  const workspaceId = currentWorkspace?.id ?? "";
  return { scope: [userId, workspaceId] as DataMapScope, ready: Boolean(userId && workspaceId) };
}
