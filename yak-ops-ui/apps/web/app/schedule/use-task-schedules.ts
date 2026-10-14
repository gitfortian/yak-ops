import { useCallback, useEffect, useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";
import { listTaskSchedules, type ScheduleSummary } from "@/service/schedule";

/** One bounded batch per visible task page, scoped to both Workspace and current IDs. */
export function useTaskSchedules(targetIds: string[]) {
  const { currentWorkspace } = useWorkspace();
  const idsKey = JSON.stringify([...new Set(targetIds)].sort());
  const key = currentWorkspace?.id + ":" + idsKey;
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{
    key: string;
    records: Map<string, ScheduleSummary>;
    error: boolean;
  }>();
  const refresh = useCallback(() => setVersion((value) => value + 1), []);
  useEffect(() => {
    const ids: string[] = JSON.parse(idsKey);
    if (!currentWorkspace?.id || ids.length === 0) {
      setState({ key, records: new Map(), error: false });
      return;
    }
    const controller = new AbortController();
    let pending = false;
    const load = async () => {
      if (pending || controller.signal.aborted) return;
      pending = true;
      try {
        const rows = await listTaskSchedules(ids, controller.signal);
        if (!controller.signal.aborted)
          setState({ key, records: new Map(rows.map((row) => [row.targetId, row])), error: false });
      } catch {
        if (!controller.signal.aborted)
          setState((current) => ({
            key,
            records: current?.key === key ? current.records : new Map(),
            error: true,
          }));
      } finally {
        pending = false;
      }
    };
    void load();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void load();
    }, 5000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [key, idsKey, currentWorkspace?.id, version]);
  return {
    records: state?.key === key ? state.records : new Map<string, ScheduleSummary>(),
    loading: state?.key !== key,
    error: state?.key === key && state.error,
    refresh,
  };
}
