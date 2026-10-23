import { useEffect, useRef, useState, type Dispatch, type MutableRefObject, type SetStateAction } from "react";
import { toast } from "@yak-ops/yak-ui";
import type { NavigateFunction } from "react-router-dom";
import { clearTaskDraft } from "@/service/data-sync/draft";
import { createDataSyncTask, updateDataSyncTask, publishDataSyncTask, type DataSyncTaskRecord, type DataSyncTaskSavePayload } from "@/service/data-sync";
import { createSchedule, updateSchedule, previewSchedule, scheduleErrorMessage, type ScheduleRecord, type ScheduleTimeRule } from "@/service/schedule";
import { EMPTY_SCHEDULE, sameScheduleRule } from "@/app/schedule/schedule-presentation";

interface SaveInput {
  id?: string;
  editing: boolean;
  draftKey: string;
  canSave: boolean;
  taskDirty: boolean;
  parametersDirty: boolean;
  supportsScheduling: boolean;
  schedule: ScheduleRecord | null;
  scheduleForm: ScheduleTimeRule;
  savedTask: MutableRefObject<DataSyncTaskRecord | undefined>;
  scheduleDraft: MutableRefObject<ScheduleTimeRule | undefined>;
  payload: () => DataSyncTaskSavePayload;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setSchedule: Dispatch<SetStateAction<ScheduleRecord | null>>;
  setScheduleForm: Dispatch<SetStateAction<ScheduleTimeRule>>;
  setScheduleError: Dispatch<SetStateAction<string>>;
  setScheduleNeedsReload: Dispatch<SetStateAction<boolean>>;
  navigate: NavigateFunction;
}

export function useEditorSubmission({
  id, editing, draftKey, canSave, taskDirty, parametersDirty, supportsScheduling,
  schedule, scheduleForm, savedTask, scheduleDraft, payload, setTaskDirty,
  setParametersDirty, setSchedule, setScheduleForm, setScheduleError,
  setScheduleNeedsReload, navigate,
}: SaveInput) {
  const [saving, setSaving] = useState(false);
  const saveSession = useRef<AbortController>();
  const basePath = "/data-sync";
  useEffect(() => {
    const controller = new AbortController();
    saveSession.current = controller;
    return () => controller.abort();
  }, []);

  const save = async (publishAfterSave = false) => {
    const signal = saveSession.current?.signal;
    if (!canSave || saving || !signal || signal.aborted) return;
    setSaving(true);
    try {
      const ruleChanged =
        supportsScheduling && !sameScheduleRule(scheduleForm, schedule || EMPTY_SCHEDULE);
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          await previewSchedule(scheduleForm, signal);
        } catch (failure) {
          if (!signal.aborted) setScheduleError(scheduleErrorMessage(failure));
          return;
        }
      }
      if (signal.aborted) return;
      const request = payload();
      const metadataRequest = { name: request.name, remark: request.remark, debug: request.debug };
      let saved = savedTask.current;
      // A completed Task write is retained across a later Schedule failure.
      if (!saved || taskDirty) {
        const savedId = id || saved?.id;
        saved = savedId
          ? await updateDataSyncTask(savedId, parametersDirty ? request : metadataRequest, signal)
          : await createDataSyncTask(request, signal);
        if (signal.aborted) return;
        savedTask.current = saved;
        clearTaskDraft(draftKey);
        setTaskDirty(false);
        setParametersDirty(false);
      }
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          const value = schedule
            ? await updateSchedule(schedule.id, schedule.revision, scheduleForm, signal)
            : await createSchedule(saved.id, scheduleForm, signal);
          if (signal.aborted) return;
          setSchedule(value);
          setScheduleForm(value);
          scheduleDraft.current = undefined;
          setScheduleError("");
        } catch (failure) {
          if (signal.aborted) return;
          const message = "任务已保存，调度未确认保存。" + scheduleErrorMessage(failure);
          setScheduleError(message);
          setScheduleNeedsReload(true);
          scheduleDraft.current = scheduleForm;
          if (!id)
            navigate(`${basePath}/${saved.id}/edit`, {
              replace: true,
              state: { scheduleDraft: scheduleForm, scheduleSaveError: message },
            });
          return;
        }
      }
      if (signal.aborted) return;
      if (publishAfterSave) {
        try {
          await publishDataSyncTask(saved.id, signal, true);
        } catch (failure) {
          if (!signal.aborted) {
            const reason = failure instanceof Error ? failure.message : "请检查同步配置";
            toast.error(`任务已保存，仍处于下线状态。上线失败：${reason}`);
            navigate(`${basePath}/${saved.id}/edit`, { replace: true });
          }
          return;
        }
        if (signal.aborted) return;
        toast.success("同步任务已保存并上线");
        navigate(basePath, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存，当前仍为已下线" : "同步任务已创建，当前为已下线");
      navigate(`${basePath}/${saved.id}/edit`, { replace: true, state: null });
    } catch {
      // Task request errors are reported by the existing transport; drafts remain editable.
    } finally {
      if (!signal.aborted) setSaving(false);
    }
  };
  return { save, saving };
}
