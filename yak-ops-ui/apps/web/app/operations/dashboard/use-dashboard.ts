import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import type { DataSyncType } from "@/service/data-sync";
import {
  getDataSyncOperations,
  type DataSyncOperationsRange,
} from "@/service/data-sync/operations";

export const operationsQueryKey = (
  userId: string,
  workspaceId: string,
  syncType: DataSyncType,
  range: DataSyncOperationsRange,
) => ["operations", "dashboard", userId, workspaceId, syncType, range] as const;

/** One server-owned dashboard snapshot per user, Workspace, sync type, and range. */
export function useOperationsDashboard(syncType: DataSyncType, range: DataSyncOperationsRange) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const userId = currentUser?.id ?? "";
  const workspaceId = currentWorkspace?.id ?? "";

  return useQuery({
    queryKey: operationsQueryKey(userId, workspaceId, syncType, range),
    queryFn: ({ signal }) => getDataSyncOperations(syncType, range, signal),
    enabled: Boolean(userId && workspaceId),
    retry: false,
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}
