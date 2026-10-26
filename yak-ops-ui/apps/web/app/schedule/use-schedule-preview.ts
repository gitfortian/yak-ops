import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { isInvalidScheduleRule, previewSchedule } from "@/service/schedule";
import { scheduleQueryKeys, useScheduleScope } from "./query-keys";

/** Delayed candidate reads are cached by user, Workspace, Cron expression and time zone. */
export function useSchedulePreview(cronExpression: string, timeZone: string) {
  const { userId, workspaceId } = useScheduleScope();
  const [debounced, setDebounced] = useState({ cronExpression, timeZone });
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced({ cronExpression, timeZone }), 250);
    return () => window.clearTimeout(timer);
  }, [cronExpression, timeZone]);

  const hasRule = Boolean(cronExpression.trim() && timeZone);
  const settled = debounced.cronExpression === cronExpression && debounced.timeZone === timeZone;
  const query = useQuery({
    queryKey: scheduleQueryKeys.preview(
      userId,
      workspaceId,
      debounced.cronExpression,
      debounced.timeZone,
    ),
    queryFn: ({ signal }) => previewSchedule(debounced, signal),
    enabled: Boolean(userId && workspaceId && hasRule && settled),
    retry: false,
    refetchOnWindowFocus: false,
  });
  const status = !cronExpression.trim()
    ? "empty"
    : !timeZone
      ? "invalid"
      : !userId || !workspaceId || !settled || query.isPending
        ? "loading"
        : query.isError
          ? isInvalidScheduleRule(query.error)
            ? "invalid"
            : "error"
          : "ready";

  return {
    status,
    times: status === "ready" ? (query.data?.nextFireTimes ?? []) : [],
    retry: () => {
      void query.refetch();
    },
  };
}
