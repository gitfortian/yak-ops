import {
  Badge,
  Button,
  PageHeader,
  SectionCard,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  toast,
} from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  getDataSyncTask,
  listDataSyncInstances,
  runDataSyncTask,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
  type DataSyncTaskRecord,
  type DataSyncType,
} from "@/service/data-sync";
import { canCancelTaskInstance } from "@/service/data-sync/execution";

import {
  DataSyncExecutionConfigContent,
  DataSyncExecutionDetailContent,
  dataSyncInstanceStatusMeta,
  dataSyncTriggerText,
  isActiveDataSyncInstance,
} from "./execution-detail";
import { TaskInstanceLogPanel } from "./task-instance-log-panel";
import { DataSyncTaskStatusBadge } from "./task-lifecycle";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;

const INSTANCE_STATUS_ITEMS: Record<DataSyncInstanceStatus | "ALL", string> = {
  ALL: "全部状态",
  SUBMITTED: "等待",
  LAUNCHING: "启动中",
  RUNNING: "运行中",
  CANCELLING: "正在停止",
  RECONCILING: "待确认",
  FINISHED: "成功",
  FAILED: "失败",
  CANCELED: "已停止",
};

interface Props {
  syncType: DataSyncType;
  basePath: string;
  localScroll?: boolean;
}

export function DataSyncTaskDetailPage({ syncType, basePath, localScroll = false }: Props) {
  const realtime = syncType === "REALTIME";
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const tab = searchParams.get("tab");
  const activeTab = tab === "config" || tab === "log" ? tab : "status";

  const [task, setTask] = useState<DataSyncTaskRecord>();
  const [taskLoading, setTaskLoading] = useState(true);
  const [instances, setInstances] = useState<DataSyncInstanceRecord[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(true);
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<DataSyncInstanceRecord>();
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const listRequest = useRef(0);

  const loadInstances = useCallback(async () => {
    if (!taskId) return;
    const request = ++listRequest.current;
    setInstancesLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId,
        syncType,
        status: status === "ALL" ? undefined : status,
      });
      if (request !== listRequest.current) return;
      setInstances(result.bizData);
      setTotal(result.pagination.total);
    } finally {
      if (request === listRequest.current) setInstancesLoading(false);
    }
  }, [pageNo, status, syncType, taskId]);

  useEffect(() => {
    if (!taskId) return;
    let disposed = false;
    setTask(undefined);
    setTaskLoading(true);
    void getDataSyncTask(taskId)
      .then((value) => {
        if (disposed) return;
        if (value.syncType !== syncType) {
          navigate(basePath, { replace: true });
          return;
        }
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
  }, [basePath, navigate, syncType, taskId]);

  useEffect(() => {
    void loadInstances();
  }, [loadInstances]);

  useEffect(() => {
    if (instancesLoading || executionId || instances.length === 0) return;
    setSearchParams(
      (previous) => {
        if (previous.has("executionId")) return previous;
        const next = new URLSearchParams(previous);
        next.set("executionId", instances[0].id);
        return next;
      },
      { replace: true },
    );
  }, [executionId, instances, instancesLoading, setSearchParams]);

  useEffect(() => {
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
        if (disposed) return;
        if (value.taskId !== taskId || value.syncType !== syncType) {
          setSearchParams(
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
        if (disposed) return;
        setSearchParams(
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
    };
  }, [executionId, setSearchParams, syncType, taskId]);

  useEffect(() => {
    if (!instances.some(isActiveDataSyncInstance) && !isActiveDataSyncInstance(selected)) return;
    let disposed = false;
    const timer = window.setInterval(() => {
      void loadInstances();
      if (!executionId) return;
      void getDataSyncInstance(executionId).then((value) => {
        if (disposed || value.taskId !== taskId || value.syncType !== syncType) return;
        setSelected(value);
      });
    }, POLL_MILLIS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [executionId, instances, loadInstances, selected, syncType, taskId]);

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
    if (!taskId || running) return;
    setRunning(true);
    try {
      const submitted = await runDataSyncTask(taskId);
      toast.success("运行请求已提交");
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
    if (!selected || !canCancelTaskInstance(selected.status) || canceling) return;
    setCanceling(true);
    try {
      await cancelDataSyncInstance(selected.id);
      toast.success("已发送停止请求");
      setSelected(await getDataSyncInstance(selected.id));
      await loadInstances();
    } finally {
      setCanceling(false);
    }
  };

  if (taskLoading || !task) {
    return (
      <div className="flex h-full items-center justify-center bg-[#f6f6f6]">
        <Spinner size="large" label="加载任务详情" />
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-[#f6f6f6] text-[#242731] md:flex-row"
          : "flex min-h-full flex-col bg-[#f6f6f6] text-[#242731] md:flex-row"
      }
    >
      <aside className="flex max-h-64 w-full shrink-0 flex-col overflow-hidden border-b border-[#e6e8eb] bg-white md:max-h-none md:w-[300px] md:border-r md:border-b-0">
        <div className="shrink-0 border-b border-[#eef0f3] px-4 py-3">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-[#242731]">执行记录</span>
            <Button
              size="small"
              variant="ghost"
              className="h-7 w-7 px-0"
              aria-label="刷新执行记录"
              title="刷新"
              disabled={instancesLoading}
              onClick={() => void loadInstances()}
            >
              <RefreshCw size={14} />
            </Button>
          </div>
          <Select
            size="small"
            items={INSTANCE_STATUS_ITEMS}
            value={status}
            onValueChange={(value) => changeStatus(String(value || "ALL") as DataSyncInstanceStatus | "ALL")}
          >
            <SelectTrigger variant="outlined" className="mt-3">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(INSTANCE_STATUS_ITEMS).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{value === "CANCELED" && !realtime ? "已取消" : label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {instancesLoading && instances.length === 0 ? (
            <div className="flex min-h-32 items-center justify-center">
              <Spinner size="medium" label="加载执行记录" />
            </div>
          ) : instances.length === 0 ? (
            <div className="flex min-h-32 items-center justify-center text-sm text-[#98a2b3]">
              暂无执行记录
            </div>
          ) : (
            <div className="space-y-1">
              {instances.map((instance) => {
                const meta = dataSyncInstanceStatusMeta(instance.status, realtime);
                const isSelected = executionId === instance.id;
                return (
                  <button
                    key={instance.id}
                    type="button"
                    aria-current={isSelected ? "true" : undefined}
                    className={
                      isSelected
                        ? "w-full cursor-pointer rounded-md bg-[#f2f5ff] px-3 py-2.5 text-left"
                        : "w-full cursor-pointer rounded-md bg-white px-3 py-2.5 text-left hover:bg-[#f6f6f6]"
                    }
                    onClick={() => selectInstance(instance.id)}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <Badge tone={meta.tone}>{meta.label}</Badge>
                      <span className="truncate text-xs text-[#98a2b3]">
                        {instance.startTime || instance.createTime || "-"}
                      </span>
                    </div>
                    <div className="mt-1 truncate text-xs text-[#667085]" title={instance.id}>
                      {instance.id}
                    </div>
                    <div className="mt-1 text-xs text-[#98a2b3]">
                      v{instance.taskVersion} · {dataSyncTriggerText(instance.triggerType)}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {total > PAGE_SIZE ? (
          <div className="flex shrink-0 items-center justify-between border-t border-[#eef0f3] px-3 py-2">
            <Button
              size="small"
              variant="ghost"
              disabled={pageNo <= 1 || instancesLoading}
              onClick={() => changePage(pageNo - 1)}
            >
              上一页
            </Button>
            <span className="text-xs text-[#98a2b3]">
              {pageNo} / {totalPages}
            </span>
            <Button
              size="small"
              variant="ghost"
              disabled={pageNo >= totalPages || instancesLoading}
              onClick={() => changePage(pageNo + 1)}
            >
              下一页
            </Button>
          </div>
        ) : null}
      </aside>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <PageHeader
          title={
            <div className="flex items-center gap-3">
              <span>{task.name}</span>
              <DataSyncTaskStatusBadge status={task.status} />
            </div>
          }
          bordered
          className="shrink-0 bg-white px-6 max-md:px-4"
          extra={
            <>
              {task.status === "PUBLISHED" ? (
                <Button size="small" loading={running} onClick={() => void run()}>
                  运行
                </Button>
              ) : null}
              <Button size="small" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            </>
          }
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-8 pt-5 max-md:px-4">
          <div className="space-y-4">
            <SectionCard title="任务配置">
              <div className="grid grid-cols-3 gap-4 max-lg:grid-cols-1">
                <div className="min-w-0">
                  <div className="text-xs text-[#98a2b3]">版本</div>
                  <div className="mt-1 text-sm text-[#344054]">v{task.definitionVersion}</div>
                </div>
                <div className="min-w-0">
                  <div className="text-xs text-[#98a2b3]">来源</div>
                  <div className="mt-1 break-all text-sm text-[#344054]">
                    {task.sourceDataSourceId} · {task.sourceTable}
                  </div>
                </div>
                <div className="min-w-0">
                  <div className="text-xs text-[#98a2b3]">去向</div>
                  <div className="mt-1 break-all text-sm text-[#344054]">
                    {task.targetDataSourceId} · {task.targetTable}
                  </div>
                </div>
              </div>
            </SectionCard>

            <Tabs value={activeTab} onValueChange={changeTab}>
              <TabsList>
                <TabsTab value="status">执行情况</TabsTab>
                <TabsTab value="config">配置快照</TabsTab>
                <TabsTab value="log">执行日志</TabsTab>
              </TabsList>

              <TabsPanel value="status" className="pt-4">
                {selected ? (
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="break-all text-xs text-[#667085]">{selected.id}</span>
                      {canCancelTaskInstance(selected.status) ? (
                        <Button
                          size="small"
                          variant="danger"
                          loading={canceling}
                          onClick={() => void cancel()}
                        >
                          停止
                        </Button>
                      ) : null}
                    </div>
                    <DataSyncExecutionDetailContent record={selected} realtime={realtime} />
                  </div>
                ) : selectedLoading ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner size="medium" label="加载实例详情" />
                  </div>
                ) : (
                  <div className="py-12 text-center text-sm text-[#98a2b3]">
                    选择左侧执行记录
                  </div>
                )}
              </TabsPanel>

              <TabsPanel value="config" className="pt-4">
                {selected ? (
                  <DataSyncExecutionConfigContent record={selected} realtime={realtime} />
                ) : selectedLoading ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner size="medium" label="加载配置快照" />
                  </div>
                ) : (
                  <div className="py-12 text-center text-sm text-[#98a2b3]">
                    选择左侧执行记录
                  </div>
                )}
              </TabsPanel>

              <TabsPanel value="log" className="pt-4">
                {selected && activeTab === "log" ? (
                  <TaskInstanceLogPanel
                    instanceId={selected.id}
                    active={isActiveDataSyncInstance(selected)}
                    embedded
                  />
                ) : selectedLoading ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner size="medium" label="加载实例详情" />
                  </div>
                ) : !selected ? (
                  <div className="py-12 text-center text-sm text-[#98a2b3]">
                    选择左侧执行记录
                  </div>
                ) : null}
              </TabsPanel>
            </Tabs>
          </div>
        </div>
      </div>
    </div>
  );
}

interface DataSyncLegacyInstanceDetailRedirectProps {
  syncType: DataSyncType;
  basePath: string;
}

export function DataSyncLegacyInstanceDetailRedirect({
  syncType,
  basePath,
}: DataSyncLegacyInstanceDetailRedirectProps) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (!id) {
      navigate(basePath, { replace: true });
      return;
    }
    void getDataSyncInstance(id)
      .then((record) => {
        if (record.syncType !== syncType) {
          navigate(basePath, { replace: true });
          return;
        }
        navigate(
          basePath + "/" + record.taskId + "/detail?executionId=" + encodeURIComponent(record.id),
          { replace: true },
        );
      })
      .catch(() => navigate(basePath, { replace: true }));
  }, [basePath, id, navigate, syncType]);

  return <div className="p-6 text-sm text-[#667085]">加载中...</div>;
}
