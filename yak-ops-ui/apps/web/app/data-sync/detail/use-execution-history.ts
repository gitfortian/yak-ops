import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { getDataSyncInstance, listDataSyncInstances, type DataSyncInstanceRecord, type DataSyncInstanceStatus } from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution";
import { isActiveDataSyncInstance } from "../execution-detail";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;

export function useExecutionHistory(taskId: string | undefined, activeInstance: TaskInstanceRecord | undefined, hasEnabledSchedule: boolean) {
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const tab = searchParams.get("tab");
  const activeTab = tab === "log" ? "log" : "status";
  const [instances, setInstances] = useState<DataSyncInstanceRecord[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(true);
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<DataSyncInstanceRecord>();
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);
  const searchParamsSetter = useRef(setSearchParams);
  const selectedInstance =
    selected?.id === executionId && selected?.taskId === taskId ? selected : undefined;
  const hasActiveInstance =
    Boolean(activeInstance) ||
    instances.some(isActiveDataSyncInstance) ||
    isActiveDataSyncInstance(selectedInstance);

  useEffect(() => {
    searchParamsSetter.current = setSearchParams;
  }, [setSearchParams]);

  const loadInstances = useCallback(async () => {
    if (!taskId) return;
    const request = ++listRequest.current;
    setInstancesLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId,
        status: status === "ALL" ? undefined : status,
      });
      if (request !== listRequest.current) return;
      setInstances(result.bizData);
      setTotal(result.pagination.total);
    } catch {
      // A failed refresh retains the last observation; the transport reports the failure.
    } finally {
      if (request === listRequest.current) setInstancesLoading(false);
    }
  }, [pageNo, status, taskId]);

  useEffect(() => {
    void loadInstances();
    return () => {
      listRequest.current += 1;
    };
  }, [loadInstances]);

  useEffect(() => {
    if (instancesLoading || executionId || instances.length === 0) return;
    searchParamsSetter.current(
      (previous) => {
        if (previous.has("executionId")) return previous;
        const next = new URLSearchParams(previous);
        next.set("executionId", instances[0].id);
        return next;
      },
      { replace: true },
    );
  }, [executionId, instances, instancesLoading]);

  useEffect(() => {
    const request = ++detailRequest.current;
    if (!executionId) {
      setSelected(undefined);
      setSelectedLoading(false);
      return;
    }

    let disposed = false;
    setSelected(undefined);
    setSelectedLoading(true);
    void getDataSyncInstance(executionId)
      .then((value) => {
        if (disposed || request !== detailRequest.current) return;
        if (value.taskId !== taskId) {
          searchParamsSetter.current(
            (previous) => {
              if (previous.get("executionId") !== executionId) return previous;
              const next = new URLSearchParams(previous);
              next.delete("executionId");
              return next;
            },
            { replace: true },
          );
          return;
        }
        setSelected(value);
      })
      .catch(() => {
        if (disposed || request !== detailRequest.current) return;
        searchParamsSetter.current(
          (previous) => {
            if (previous.get("executionId") !== executionId) return previous;
            const next = new URLSearchParams(previous);
            next.delete("executionId");
            return next;
          },
          { replace: true },
        );
      })
      .finally(() => {
        if (!disposed) setSelectedLoading(false);
      });

    return () => {
      disposed = true;
      detailRequest.current += 1;
    };
  }, [executionId, taskId]);

  useEffect(() => {
    if (!autoRefresh || (!hasActiveInstance && !hasEnabledSchedule) || selectedLoading) return;
    let disposed = false;
    let pending = false;
    const timer = window.setInterval(async () => {
      if (pending) return;
      pending = true;
      const request = ++detailRequest.current;
      try {
        await loadInstances();
        if (!executionId || disposed) return;
        const value = await getDataSyncInstance(executionId);
        if (disposed || request !== detailRequest.current) return;
        if (value.taskId === taskId) setSelected(value);
      } catch {
        // Keep the last observation; the HTTP boundary reports request failures.
      } finally {
        pending = false;
      }
    }, POLL_MILLIS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [
    autoRefresh,
    executionId,
    hasActiveInstance,
    hasEnabledSchedule,
    loadInstances,
    selectedLoading,
    taskId,
  ]);

  const refreshExecution = async () => {
    if (refreshing) return;
    const request = ++detailRequest.current;
    setRefreshing(true);
    try {
      await loadInstances();
      if (!executionId || request !== detailRequest.current) return;
      const value = await getDataSyncInstance(executionId);
      if (request !== detailRequest.current) return;
      if (value.taskId === taskId) setSelected(value);
    } finally {
      setRefreshing(false);
    }
  };

  const selectInstance = (id: string) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("executionId", id);
      return next;
    });
  };

  const changeTab = (value: string) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      if (value === "status") next.delete("tab");
      else next.set("tab", value);
      return next;
    });
  };

  const changePage = (page: number) => {
    setPageNo(page);
    setInstances([]);
    setInstancesLoading(true);
    setSelected(undefined);
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.delete("executionId");
        return next;
      },
      { replace: true },
    );
  };

  const changeStatus = (value: DataSyncInstanceStatus | "ALL") => {
    setStatus(value);
    changePage(1);
  };

  return {
    executionId, activeTab, instances, instancesLoading, status, pageNo, total,
    selectedInstance, selectedLoading, autoRefresh, setAutoRefresh,
    refreshing, loadInstances, refreshExecution,
    selectInstance, changeTab, changePage, changeStatus,
    detailRequest, setSelected, setStatus, setPageNo, setInstances, setInstancesLoading,
    setSearchParams,
  };
}
