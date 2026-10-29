import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  PageHeader,
  Pagination,
  toast,
} from "@yak-ops/yak-ui";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LoaderCircle, Pencil, Play, Power, PowerOff, Trash2 } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { changeTaskStatus, deleteTask, listTasks, runTask } from "@/service/data-map/task";
import type { CrawlTask, CrawlTaskStatus } from "@/service/data-map/types";
import { dataMapFailureMessage } from "@/service/data-map/request";

import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import {
  CRAWL_STATUS_LABEL,
  DATA_MAP_BASE,
  scheduleLabel,
  TASK_STATUS_LABEL,
  TASK_STATUS_TONE,
} from "../shared/labels";
import { useDatasourceOptions } from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";

const DEFAULT_PAGE_SIZE = 20;
const ACTIVE_POLL_MS = 5000;

const readPageNo = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
};

const isRunActive = (status: string | null) => status === "PENDING" || status === "RUNNING";

interface ConfirmRequest {
  kind: "offline" | "delete";
  ids: string[];
}

const tasksPath = `${DATA_MAP_BASE}/tasks`;

function StatusDot({ status }: { status: CrawlTaskStatus }) {
  const running = status === "RUNNING";
  return (
    <span className="relative flex size-2.5 shrink-0" aria-hidden="true">
      {running && (
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--yak-color-primary)] opacity-50 motion-reduce:animate-none" />
      )}
      <span
        className={
          running
            ? "relative inline-flex size-2.5 rounded-full bg-[var(--yak-color-primary)]"
            : "relative inline-flex size-2.5 rounded-full bg-[var(--yak-components-muted-text)]/45"
        }
      />
    </span>
  );
}

function RowAction({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Button
      size="small"
      variant="ghost"
      aria-label={label}
      title={label}
      disabled={disabled}
      className="size-7 !p-0"
      onClick={onClick}
    >
      {children}
    </Button>
  );
}

export default function CrawlTasksPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const { scope, ready } = useDataMapScope();
  const pageNo = readPageNo(searchParams.get("pageNo"));
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [confirm, setConfirm] = useState<ConfirmRequest | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CrawlTask | null>(null);

  const datasources = useDatasourceOptions();
  const datasourceNames = new Map(datasources.options.map((item) => [item.id, item.name]));

  const page = useQuery({
    queryKey: dataMapKeys.tasks(scope, pageNo, DEFAULT_PAGE_SIZE),
    queryFn: ({ signal }) => listTasks({ pageNo, pageSize: DEFAULT_PAGE_SIZE }, signal),
    enabled: ready,
    placeholderData: keepPreviousData,
    refetchInterval: (query) => {
      const records = query.state.data?.bizData ?? [];
      return records.some((task) => task.status === "RUNNING" || isRunActive(task.lastRunStatus))
        ? ACTIVE_POLL_MS
        : false;
    },
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;
  const isEmpty = page.isSuccess && records.length === 0;

  useEffect(() => {
    setSelected(new Set());
  }, [pageNo]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tasks"] });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "task"] });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawls"] });
  };

  const run = useMutation({
    mutationFn: (taskId: string) => runTask(taskId),
    onSuccess: () => {
      invalidate();
      toast.success("已提交一次手动采集");
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "手动执行失败，请稍后重试")),
  });

  const toggle = useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: CrawlTaskStatus }) =>
      changeTaskStatus(taskId, status),
    onSuccess: (_task, variables) => {
      invalidate();
      toast.success(variables.status === "RUNNING" ? "任务已上线" : "任务已下线");
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "任务状态更新失败")),
  });

  const remove = useMutation({
    mutationFn: (taskId: string) => deleteTask(taskId),
    onSuccess: () => {
      invalidate();
      toast.success("任务已删除");
      setDeleteTarget(null);
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "任务删除失败")),
  });

  const batch = useMutation({
    mutationFn: async ({ kind, ids }: ConfirmRequest) => {
      const results = await Promise.allSettled(
        ids.map((id) => (kind === "offline" ? changeTaskStatus(id, "OFFLINE") : deleteTask(id))),
      );
      const failed = results.filter((result) => result.status === "rejected").length;
      return { kind, total: ids.length, failed };
    },
    onSuccess: ({ kind, total, failed }) => {
      invalidate();
      setConfirm(null);
      setSelected(new Set());
      if (failed === 0) {
        toast.success(kind === "offline" ? `已下线 ${total} 个任务` : `已删除 ${total} 个任务`);
      } else {
        toast.warning(`${total} 个任务中有 ${failed} 个未完成，请刷新后确认`);
      }
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "批量操作失败")),
  });

  const selectedTasks = records.filter((task) => selected.has(task.id));
  const allSelected = records.length > 0 && records.every((task) => selected.has(task.id));
  const runningSelected = selectedTasks.filter((task) => task.status === "RUNNING");

  const toggleSelected = (taskId: string, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) next.add(taskId);
      else next.delete(taskId);
      return next;
    });
  };

  const requestDelete = (task: CrawlTask) => {
    if (task.status === "RUNNING") {
      toast.error("运行中的任务不能删除，请先下线");
      return;
    }
    setDeleteTarget(task);
  };

  const requestBatchDelete = () => {
    if (runningSelected.length > 0) {
      toast.error("所选任务中包含运行中的任务，请先下线后再删除");
      return;
    }
    setConfirm({ kind: "delete", ids: selectedTasks.map((task) => task.id) });
  };

  const writePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    if (nextPage > 1) next.set("pageNo", String(nextPage));
    else next.delete("pageNo");
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="元数据采集"
        description="维护采集任务、调度与排除规则。"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
        extra={
          <Button size="small" variant="primary" onClick={() => navigate(`${tasksPath}/new`)}>
            新建采集任务
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 pt-5 max-md:px-4">
        <Card className="flex min-h-0 flex-1 flex-col p-4">
          <div className="flex shrink-0 flex-wrap items-center gap-3">
            <Checkbox
              aria-label="全选本页任务"
              checked={allSelected}
              disabled={records.length === 0}
              onCheckedChange={(checked) =>
                setSelected(new Set(checked ? records.map((task) => task.id) : []))
              }
            />
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              共 {total} 个采集任务
              {selected.size > 0 ? ` · 已选 ${selected.size} 个` : ""}
            </span>
            {page.isError && (
              <Button size="small" className="ml-auto" onClick={() => void page.refetch()}>
                重试
              </Button>
            )}
          </div>

          {page.isError && (
            <Alert className="mt-2">
              {dataMapFailureMessage(page.error, "采集任务加载失败，请稍后重试。")}
            </Alert>
          )}

          <div className="mt-3 min-h-0 flex-1 overflow-auto">
            {page.isPending ? (
              <div className="py-16 text-center text-sm text-[var(--yak-components-muted-text)]">
                正在加载采集任务…
              </div>
            ) : isEmpty ? (
              <DataMapEmptyState
                title="还没有采集任务"
                description="新建一个采集任务，选择数据源与调度方式，平台会按配置同步表结构到资产目录。"
                action={
                  <Button
                    size="small"
                    variant="primary"
                    onClick={() => navigate(`${tasksPath}/new`)}
                  >
                    新建采集任务
                  </Button>
                }
              />
            ) : (
              <div className="space-y-2">
                {records.map((task) => {
                  const statusBusy =
                    (toggle.isPending && toggle.variables?.taskId === task.id) ||
                    (run.isPending && run.variables === task.id);
                  return (
                    <Card
                      key={task.id}
                      className="group flex items-center gap-3 px-4 py-3 transition-[border-color,box-shadow] hover:border-[var(--yak-components-control-border)] hover:shadow-sm"
                    >
                      <Checkbox
                        aria-label={`选择任务 ${task.name}`}
                        checked={selected.has(task.id)}
                        onCheckedChange={(checked) => toggleSelected(task.id, checked)}
                      />
                      <StatusDot status={task.status} />

                      <div className="min-w-0 flex-1">
                        <div className="flex min-w-0 items-center gap-2">
                          <span className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]">
                            {task.name}
                          </span>
                          <Badge tone={TASK_STATUS_TONE[task.status]}>
                            {TASK_STATUS_LABEL[task.status]}
                          </Badge>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--yak-components-muted-text)]">
                          <span className="truncate">
                            {datasourceNames.get(task.datasourceId) ?? task.datasourceId}
                            {" · "}
                            {task.database}
                            {task.schema ? ` / ${task.schema}` : ""}
                          </span>
                          <span>{scheduleLabel(task.scheduleType, task.cronExpression)}</span>
                          {task.status === "RUNNING" && task.nextFireTime && (
                            <span>下次 {task.nextFireTime}</span>
                          )}
                          <span className="inline-flex items-center gap-1">
                            {isRunActive(task.lastRunStatus) && (
                              <LoaderCircle
                                size={11}
                                className="animate-spin motion-reduce:animate-none"
                                aria-hidden="true"
                              />
                            )}
                            最近运行
                            {task.lastRunStatus
                              ? ` · ${CRAWL_STATUS_LABEL[task.lastRunStatus]}`
                              : " · 尚未运行"}
                            {task.lastRunTime ? ` · ${task.lastRunTime}` : ""}
                          </span>
                        </div>
                      </div>

                      <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100 max-md:opacity-100">
                        <RowAction
                          label="编辑"
                          onClick={() => navigate(`${tasksPath}/${task.id}/edit`)}
                        >
                          <Pencil size={14} aria-hidden="true" />
                        </RowAction>
                        <RowAction
                          label={task.status === "RUNNING" ? "立即运行" : "上线后可立即运行"}
                          disabled={task.status !== "RUNNING" || statusBusy}
                          onClick={() => run.mutate(task.id)}
                        >
                          <Play size={14} aria-hidden="true" />
                        </RowAction>
                        <RowAction
                          label={task.status === "RUNNING" ? "下线" : "上线"}
                          disabled={statusBusy}
                          onClick={() =>
                            toggle.mutate({
                              taskId: task.id,
                              status: task.status === "RUNNING" ? "OFFLINE" : "RUNNING",
                            })
                          }
                        >
                          {task.status === "RUNNING" ? (
                            <PowerOff size={14} aria-hidden="true" />
                          ) : (
                            <Power size={14} aria-hidden="true" />
                          )}
                        </RowAction>
                        <RowAction
                          label={task.status === "RUNNING" ? "运行中的任务不能删除" : "删除"}
                          disabled={task.status === "RUNNING"}
                          onClick={() => requestDelete(task)}
                        >
                          <Trash2 size={14} aria-hidden="true" />
                        </RowAction>
                      </div>
                    </Card>
                  );
                })}
              </div>
            )}
          </div>

          {total > DEFAULT_PAGE_SIZE && (
            <div className="mt-3 shrink-0 border-t border-[var(--yak-components-panel-border)] pt-3">
              <Pagination
                page={pageNo}
                pageSize={DEFAULT_PAGE_SIZE}
                total={total}
                onChange={(nextPage) => writePage(nextPage)}
              />
            </div>
          )}
        </Card>
      </div>

      {selected.size > 0 && (
        <div className="fixed bottom-6 left-1/2 z-30 flex -translate-x-1/2 items-center gap-3 rounded-full border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] py-2 pl-5 pr-2 shadow-[var(--yak-shadow-md)]">
          <span className="text-xs text-[var(--yak-components-panel-text)]">
            已选 {selected.size} 个任务
          </span>
          <Button
            size="small"
            onClick={() =>
              setConfirm({ kind: "offline", ids: selectedTasks.map((task) => task.id) })
            }
          >
            批量下线
          </Button>
          <Button size="small" variant="danger" onClick={requestBatchDelete}>
            批量删除
          </Button>
          <Button size="small" variant="ghost" onClick={() => setSelected(new Set())}>
            取消
          </Button>
        </div>
      )}

      <Dialog
        open={confirm !== null}
        onOpenChange={(open) => {
          if (!open && !batch.isPending) setConfirm(null);
        }}
      >
        <DialogContent className="w-[430px]">
          <DialogTitle className="text-sm font-medium">
            {confirm?.kind === "delete"
              ? `删除 ${confirm.ids.length} 个采集任务？`
              : `下线 ${confirm?.ids.length ?? 0} 个采集任务？`}
          </DialogTitle>
          <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            {confirm?.kind === "delete"
              ? "删除后任务配置与全部运行实例都会被永久删除，无法恢复。"
              : "下线后任务停止调度并保留配置与历史，可随时重新上线。"}
          </DialogDescription>
          {confirm?.kind === "delete" && runningSelected.length > 0 && (
            <Alert className="mt-3">所选任务中包含运行中的任务，请先下线后再删除。</Alert>
          )}
          <div className="mt-5 flex justify-end gap-2">
            <Button size="small" disabled={batch.isPending} onClick={() => setConfirm(null)}>
              取消
            </Button>
            <Button
              size="small"
              variant={confirm?.kind === "delete" ? "danger" : "primary"}
              loading={batch.isPending}
              disabled={confirm?.ids.length === 0}
              onClick={() => confirm && batch.mutate(confirm)}
            >
              确认
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) setDeleteTarget(null);
        }}
      >
        <DialogContent className="w-[430px]">
          <DialogTitle className="text-sm font-medium">
            删除采集任务「{deleteTarget?.name}」？
          </DialogTitle>
          <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            删除后该任务的配置与全部运行实例都会被永久删除，无法恢复。已采集的资产会保留。
          </DialogDescription>
          <div className="mt-5 flex justify-end gap-2">
            <Button size="small" disabled={remove.isPending} onClick={() => setDeleteTarget(null)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={remove.isPending}
              onClick={() => deleteTarget && remove.mutate(deleteTarget.id)}
            >
              删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
