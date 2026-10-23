import { useEffect, useRef, useState } from "react";
import { getTaskSchedule, scheduleErrorMessage, type ScheduleRecord, type ScheduleTimeRule } from "@/service/schedule";
import { EMPTY_SCHEDULE } from "@/app/schedule/schedule-presentation";
import type { DataSyncTaskRecord } from "@/service/data-sync";

interface PendingSchedule { scheduleDraft?: ScheduleTimeRule; scheduleSaveError?: string }
export function useEditorSchedule(id: string | undefined, supportsScheduling: boolean, initialTask: DataSyncTaskRecord | undefined, pendingSchedule: PendingSchedule | null) {
  const [schedule, setSchedule] = useState<ScheduleRecord | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleTimeRule>(
    pendingSchedule?.scheduleDraft || { ...EMPTY_SCHEDULE },
  );
  const [scheduleLoading, setScheduleLoading] = useState(
    Boolean(initialTask && initialTask.syncType === "OFFLINE"),
  );
  const [scheduleError, setScheduleError] = useState(pendingSchedule?.scheduleSaveError || "");
  const [scheduleNeedsReload, setScheduleNeedsReload] = useState(
    Boolean(pendingSchedule?.scheduleSaveError),
  );
  const [scheduleReload, setScheduleReload] = useState(0);
  const scheduleDraft = useRef(pendingSchedule?.scheduleDraft);

  useEffect(() => {
    if (!supportsScheduling || !id) return;
    const controller = new AbortController();
    setScheduleLoading(true);
    void getTaskSchedule(id, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setSchedule(value);
        if (!scheduleDraft.current) setScheduleForm(value || { ...EMPTY_SCHEDULE });
        if (scheduleReload > 0) {
          setScheduleError("");
          setScheduleNeedsReload(false);
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setScheduleError(scheduleErrorMessage(failure));
          setScheduleNeedsReload(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setScheduleLoading(false);
      });
    return () => controller.abort();
  }, [id, supportsScheduling, scheduleReload]);

  const reloadSchedule = () => {
    scheduleDraft.current = scheduleForm;
    setScheduleReload((value) => value + 1);
  };
  const changeSchedule = (value: ScheduleTimeRule) => {
    setScheduleForm(value);
    scheduleDraft.current = value;
    setScheduleError("");
  };
  return {
    schedule, scheduleForm, scheduleLoading, scheduleError, scheduleNeedsReload,
    scheduleDraft, setSchedule, setScheduleForm, setScheduleError,
    setScheduleNeedsReload, reloadSchedule, changeSchedule,
  };
}
