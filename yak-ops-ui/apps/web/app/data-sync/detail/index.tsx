import { Button, PageHeader, Spinner, toast } from "@yak-ops/yak-ui";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ScheduleDialog } from "@/app/schedule/schedule-dialog";
import { useTaskSchedules } from "@/app/schedule/use-task-schedules";
import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import { getUsersByIds } from "@/service/user";
import {
  cancelDataSyncInstance, getDataSyncInstance, getDataSyncTask, listDataSyncInstances,
  runDataSyncTask, type DataSyncInstanceRecord, type DataSyncInstanceStatus,
  type DataSyncTaskRecord
} from "@/service/data-sync";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import { listLatestTaskInstances } from "@/service/task-execution";
import { isActiveDataSyncInstance } from "../execution-detail";
import { useActiveTaskInstances } from "../task-lifecycle";
import { ExecutionSidebar } from "./execution-sidebar";
import { TaskSummary } from "./task-summary";
import { ExecutionTabs } from "./execution-tabs";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;

export function DataSyncTaskDetailPage() {
  const basePath = "/data-sync";
  const localScroll = true;
  const { id: taskId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const tab = searchParams.get("tab");
  const activeTab = tab === "log" ? "log" : "status";

  const [task, setTask] = useState<DataSyncTaskRecord>();
  const realtime = task?.syncType === "REALTIME";
  const [taskLoading, setTaskLoading] = useState(true);
  const [dataSources, setDataSources] = useState<{
    source?: DataSourceRecord;
    target?: DataSourceRecord;
  }>({});
  const [updatedBy, setUpdatedBy] = useState("-");
  const [instances, setInstances] = useState<DataSyncInstanceRecord[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(true);
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<DataSyncInstanceRecord>();
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [historyScheduleId, setHistoryScheduleId] = useState<string>();
  const schedules = useTaskSchedules(!realtime && taskId ? [taskId] : []);
  const hasEnabledSchedule = Boolean(taskId && schedules.records.get(taskId)?.enabled);
  const activeTaskIds = useMemo(() => (taskId ? [taskId] : []), [taskId]);
  const {
    activeByTask,
    ready: instancesReady,
    refresh: refreshActiveInstances,
  } = useActiveTaskInstances(Boolean(taskId), activeTaskIds);
  const activeInstance = taskId ? activeByTask.get(taskId) : undefined;
  const canStart = canManageTaskDefinition(instancesReady, activeInstance);
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
    if (!taskId) return;
    let disposed = false;
    setTask(undefined);
    setTaskLoading(true);
    void getDataSyncTask(taskId)
      .then((value) => {
        if (disposed) return;
        setTask(value);
      })
      .catch(() => {
        if (!disposed) navigate(basePath, { replace: true });
      })
      .finally(() => {
        if (!disposed) setTaskLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, [basePath, navigate, taskId]);

  useEffect(() => {
    const sourceId = task?.sourceDataSourceId;
    const targetId = task?.targetDataSourceId;
    if (!sourceId || !targetId) {
      setDataSources({});
      return;
    }
    let disposed = false;
    setDataSources({});
    void Promise.all([
      getDataSource(sourceId).catch(() => undefined),
      getDataSource(targetId).catch(() => undefined),
    ]).then(([source, target]) => {
      if (!disposed) setDataSources({ source, target });
    });
    return () => {
      disposed = true;
    };
  }, [task?.sourceDataSourceId, task?.targetDataSourceId]);

  useEffect(() => {
    const userId = task?.updateBy;
    if (!userId || userId === "system") {
      setUpdatedBy(userId === "system" ? "SYSTEM" : "-");
      return;
    }
    let disposed = false;
    setUpdatedBy("-");
    void getUsersByIds([userId])
      .then((users) => {
        if (!disposed) setUpdatedBy(users[0]?.realName || users[0]?.userName || "-");
      })
      .catch(() => {
        if (!disposed) setUpdatedBy("-");
      });
    return () => {
      disposed = true;
    };
  }, [task?.updateBy]);

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

  if (taskLoading || !task) {
    return (
      <div className="flex h-full items-center justify-center bg-white">
        <Spinner size="large" label="加载任务详情" />
      </div>
    );
  }


  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-white text-[#242731] md:flex-row"
          : "flex min-h-full flex-col bg-white text-[#242731] md:flex-row"
      }
    >
      <ExecutionSidebar
        realtime={realtime}
        status={status}
        onStatusChange={changeStatus}
        instances={instances}
        instancesLoading={instancesLoading}
        selectedId={executionId}
        onSelect={selectInstance}
        total={total}
        pageNo={pageNo}
        onPageChange={changePage}
        onRefresh={() => void loadInstances()}
      />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <PageHeader
          title={task.name}
          bordered
          className="shrink-0 bg-white px-6 max-md:px-4"
          extra={
            <>
              {!realtime ? (
                <Button
                  size="small"
                  onClick={() => {
                    setHistoryScheduleId(undefined);
                    setScheduleOpen(true);
                  }}
                >
                  调度
                </Button>
              ) : null}
              {task.status === "PUBLISHED" ? (
                !activeInstance ? (
                  <Button
                    size="small"
                    loading={running}
                    disabled={!canStart}
                    onClick={() => void run()}
                  >
                    {realtime ? "启动" : "运行"}
                  </Button>
                ) : null
              ) : (
                <Button
                  size="small"
                  disabled={!canStart}
                  onClick={() => navigate(basePath + "/" + task.id + "/edit")}
                >
                  编辑
                </Button>
              )}
              <Button size="small" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            </>
          }
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-5 pt-5 max-md:px-4">
          <div className="space-y-8">
            <TaskSummary
              task={task}
              updatedBy={updatedBy}
              dataSources={dataSources}
              onCopyTaskId={() => void copyTaskId()}
            />
            <ExecutionTabs
              activeTab={activeTab}
              onChangeTab={changeTab}
              selectedInstance={selectedInstance}
              selectedLoading={selectedLoading}
              instanceCount={instances.length}
              autoRefresh={autoRefresh}
              onAutoRefreshChange={setAutoRefresh}
              refreshing={refreshing}
              onRefresh={() => void refreshExecution()}
              canceling={canceling}
              onCancel={() => void cancel()}
              onViewScheduleHistory={(id) => {
                setHistoryScheduleId(id);
                setScheduleOpen(true);
              }}
            />
          </div>
        </div>
      </div>
      {!realtime ? (
        <ScheduleDialog
          targetId={task.id}
          published={task.status === "PUBLISHED"}
          open={scheduleOpen}
          historyScheduleId={historyScheduleId}
          onClose={() => setScheduleOpen(false)}
          onChanged={schedules.refresh}
          onViewInstance={(id) => {
            setStatus("ALL");
            setPageNo(1);
            setSearchParams((previous) => {
              const next = new URLSearchParams(previous);
              next.set("executionId", id);
              next.delete("tab");
              return next;
            });
            void loadInstances();
          }}
        />
      ) : null}
    </div>
  );
}
