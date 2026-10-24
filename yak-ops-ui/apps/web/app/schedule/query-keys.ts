import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";

export const scheduleQueryKeys = {
  all: ["schedule"] as const,
  tasks: () => [...scheduleQueryKeys.all, "tasks"] as const,
  targets: (userId: string, workspaceId: string, ids: readonly string[]) =>
    [...scheduleQueryKeys.tasks(), userId, workspaceId, ...ids] as const,
  detail: (userId: string, workspaceId: string, targetId: string) =>
    [...scheduleQueryKeys.all, "detail", userId, workspaceId, targetId] as const,
  preview: (userId: string, workspaceId: string, cron: string, zone: string) =>
    [...scheduleQueryKeys.all, "preview", userId, workspaceId, cron, zone] as const,
  fires: (userId: string, workspaceId: string, scheduleId: string, pageNo: number, outcome: string, from: string, until: string) =>
    [...scheduleQueryKeys.all, "fires", userId, workspaceId, scheduleId, pageNo, outcome, from, until] as const,
};

export function useScheduleScope() {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  return { userId: currentUser?.id ?? "", workspaceId: currentWorkspace?.id ?? "" };
}

/** The tab is unmounted when hidden. Poll only while the document is visible. */
export const visiblePollInterval = () => document.visibilityState === "visible" ? 5000 : false;
