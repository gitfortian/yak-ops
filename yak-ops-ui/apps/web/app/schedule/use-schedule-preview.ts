import { useEffect, useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";
import { isInvalidScheduleRule, previewSchedule } from "@/service/schedule";

type PreviewState = {
  key: string;
  status: "loading" | "ready" | "invalid" | "error";
  times: string[];
};

export function useSchedulePreview(cronExpression: string, timeZone: string) {
  const { currentWorkspace } = useWorkspace();
  const key = JSON.stringify([currentWorkspace?.id, cronExpression, timeZone]);
  const [state, setState] = useState<PreviewState>();
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    if (!cronExpression.trim() || !timeZone || !currentWorkspace?.id) return;
    const controller = new AbortController();
    setState({ key, status: "loading", times: [] });
    const timer = window.setTimeout(() => {
      void previewSchedule({ cronExpression, timeZone }, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted)
            setState({ key, status: "ready", times: result.nextFireTimes });
        })
        .catch((error: unknown) => {
          if (!controller.signal.aborted)
            setState({
              key,
              status: isInvalidScheduleRule(error) ? "invalid" : "error",
              times: [],
            });
        });
    }, 250);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [key, cronExpression, timeZone, currentWorkspace?.id, refresh]);
  return {
    status: !cronExpression.trim() ? "empty" : state?.key === key ? state.status : "loading",
    times: state?.key === key ? state.times : [],
    retry: () => setRefresh((value) => value + 1),
  };
}
