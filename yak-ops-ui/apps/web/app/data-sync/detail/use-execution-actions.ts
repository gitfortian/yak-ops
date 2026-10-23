import { useState, type MutableRefObject, type Dispatch, type SetStateAction } from "react";
import { toast } from "@yak-ops/yak-ui";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import { cancelDataSyncInstance, getDataSyncInstance, runDataSyncTask, type DataSyncInstanceRecord, type DataSyncInstanceStatus, type DataSyncTaskRecord } from "@/service/data-sync";
import { listLatestTaskInstances } from "@/service/task-execution";
import type { SetURLSearchParams } from "react-router-dom";

interface ActionsInput {
  taskId?: string;
  task?: DataSyncTaskRecord;
  canStart: boolean;
  selectedInstance?: DataSyncInstanceRecord;
  detailRequest: MutableRefObject<number>;
  loadInstances: () => Promise<void>;
  refreshActiveInstances: () => Promise<void>;
  pageNo: number;
  status: DataSyncInstanceStatus | "ALL";
  setStatus: Dispatch<SetStateAction<DataSyncInstanceStatus | "ALL">>;
  setPageNo: Dispatch<SetStateAction<number>>;
  setInstances: Dispatch<SetStateAction<DataSyncInstanceRecord[]>>;
  setInstancesLoading: Dispatch<SetStateAction<boolean>>;
  setSelected: Dispatch<SetStateAction<DataSyncInstanceRecord | undefined>>;
  setSearchParams: SetURLSearchParams;
}

export function useExecutionActions({ taskId, task, canStart, selectedInstance, detailRequest,
  loadInstances, refreshActiveInstances, pageNo, status, setStatus, setPageNo,
  setInstances, setInstancesLoading, setSelected, setSearchParams }: ActionsInput) {
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const run = async () => {
    if (!taskId || running || !canStart || task?.status !== "PUBLISHED") return;
    setRunning(true);
    try {
      const active = await listLatestTaskInstances([taskId], true);
      if (active.length > 0) {
        toast.error("任务正在执行，请先停止");
        await refreshActiveInstances();
        return;
      }
      const submitted = await runDataSyncTask(taskId);
      toast.success("运行请求已提交");
      await refreshActiveInstances();
      setStatus("ALL");
      setPageNo(1);
      setInstances([]);
      setInstancesLoading(true);
      setSearchParams((previous) => {
        const next = new URLSearchParams(previous);
        next.set("executionId", submitted.instanceId);
        next.delete("tab");
        return next;
      });
      if (pageNo === 1 && status === "ALL") await loadInstances();
    } finally {
      setRunning(false);
    }
  };

  const cancel = async () => {
    if (!selectedInstance || !canCancelTaskInstance(selectedInstance.status) || canceling) return;
    const request = ++detailRequest.current;
    setCanceling(true);
    try {
      await cancelDataSyncInstance(selectedInstance.id);
      toast.success("已发送停止请求");
      const value = await getDataSyncInstance(selectedInstance.id);
      if (request === detailRequest.current) setSelected(value);
      await Promise.all([loadInstances(), refreshActiveInstances()]);
    } finally {
      setCanceling(false);
    }
  };

  const copyTaskId = async () => {
    if (!task) return;
    try {
      await navigator.clipboard.writeText(task.id);
      toast.success("已复制任务 ID");
    } catch {
      toast.error("复制失败");
    }
  };

  return { run, cancel, copyTaskId, running, canceling };
}
