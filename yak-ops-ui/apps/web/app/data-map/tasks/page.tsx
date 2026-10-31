import { Alert, Button, Card, PageHeader, Pagination, toast } from "@yak-ops/yak-ui";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { getCrawlSummary, listCrawls } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";
import { changeTaskStatus, deleteTask, listTasks, runTask } from "@/service/data-map/task";
import type {
  Crawl,
  CrawlSummary,
  CrawlTask,
  CrawlTaskStatus,
  TaskQuery,
} from "@/service/data-map/types";

import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE, readTaskStatus } from "../shared/labels";
import { useDatasourceOptions, useOwnerOptions } from "../shared/lookups";
import { useDataMapScope } from "../shared/scope";
import HealthBand from "./components/health-band";
import { BatchConfirmDialog, DeleteTaskDialog } from "./components/task-confirm";
import TaskDrawer from "./components/task-drawer";
import TaskFilters from "./components/task-filters";
import TaskTable from "./components/task-table";
import TaskTabs from "./components/task-tabs";

const DEFAULT_PAGE_SIZE = 20;
const ACTIVE_POLL_MS = 5000;
/** Runs read for the "newest instance per task" join; wide enough for a full page of tasks. */
const LATEST_RUN_WINDOW = 50;
/** The band's run window; fixed so the strip cannot silently change meaning between loads. */
const SUMMARY_HOURS = 24;
/** Shown until the first summary arrives; zeros read as "nothing known", not as a real state. */
const EMPTY_SUMMARY: CrawlSummary = {
  taskTotal: 0,
  onlineTaskCount: 0,
  windowHours: SUMMARY_HOURS,
  runTotal: 0,
  runSucceeded: 0,
  runPartial: 0,
  runFailed: 0,
  runInterrupted: 0,
  runRunning: 0,
  runPending: 0,
  failedTableCount: 0,
  abnormalTaskCount: 0,
  columnTotal: 0,
  describedColumnCount: 0,
  todayRunTotal: 0,
  todayRunSucceeded: 0,
  weekRunTotal: 0,
  weekRunSucceeded: 0,
  avgDurationSeconds: 0,
  p95DurationSeconds: 0,
};

const readPageNo = (value: string | null) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
};

const isRunActive = (status: string | null | undefined) =>
  status === "PENDING" || status === "RUNNING";

const tasksPath = `${DATA_MAP_BASE}/tasks`;

interface ConfirmRequest {
  kind: "offline" | "delete";
  ids: string[];
}

export default function CrawlTasksPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const { scope, ready } = useDataMapScope();
  const pageNo = readPageNo(searchParams.get("pageNo"));

  const keyword = searchParams.get("q")?.trim() ?? "";
  const statusFilter = readTaskStatus(searchParams.get("status"));
  const datasourceFilter = searchParams.get("ds") ?? "";
  const onlyAbnormal = searchParams.get("abnormal") === "1";

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [confirm, setConfirm] = useState<ConfirmRequest | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CrawlTask | null>(null);
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);

  const datasources = useDatasourceOptions();
  const datasourceNames = useMemo(
    () => new Map(datasources.options.map((item) => [item.id, item.name])),
    [datasources.options],
  );
  const owners = useOwnerOptions();
  const ownerNames = useMemo(
    () => new Map((owners.data ?? []).map((item) => [item.userId, item.displayName])),
    [owners.data],
  );

  // Every filter, including the abnormal shortcut, is applied by the server: the band counts the
  // whole workspace, so a filter that only hid page rows would disagree with the number above.
  const taskQuery: TaskQuery = {
    pageNo,
    pageSize: DEFAULT_PAGE_SIZE,
    keyword: keyword || undefined,
    status: statusFilter,
    datasourceId: datasourceFilter || undefined,
    abnormal: onlyAbnormal || undefined,
  };

  const page = useQuery({
    queryKey: dataMapKeys.tasks(scope, taskQuery),
    queryFn: ({ signal }) => listTasks(taskQuery, signal),
    enabled: ready,
    placeholderData: keepPreviousData,
    refetchInterval: (query) => {
      const records = query.state.data?.bizData ?? [];
      return records.some((task) => isRunActive(task.lastRunStatus)) ? ACTIVE_POLL_MS : false;
    },
  });

  /**
   * The task list carries only the last run status, so the newest instance of each task is joined
   * from the run feed. A task whose newest run falls outside the window keeps the list's status and
   * shows "-" for counts instead of claiming zero.
   */
  const recentRuns = useQuery({
    queryKey: dataMapKeys.taskCrawls(scope, "", LATEST_RUN_WINDOW),
    queryFn: ({ signal }) => listCrawls({ pageNo: 1, pageSize: LATEST_RUN_WINDOW }, signal),
    enabled: ready,
    refetchInterval: (query) =>
      (query.state.data?.bizData ?? []).some((crawl) => isRunActive(crawl.status))
        ? ACTIVE_POLL_MS
        : false,
  });

  const summary = useQuery({
    queryKey: dataMapKeys.crawlSummary(scope, SUMMARY_HOURS),
    queryFn: ({ signal }) => getCrawlSummary(SUMMARY_HOURS, signal),
    enabled: ready,
    refetchInterval: (query) =>
      (query.state.data?.runRunning ?? 0) + (query.state.data?.runPending ?? 0) > 0
        ? ACTIVE_POLL_MS
        : false,
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;
  const isEmpty = page.isSuccess && records.length === 0;

  const latestByTask = useMemo(() => {
    const map = new Map<string, Crawl>();
    [...(recentRuns.data?.bizData ?? [])]
      .sort((a, b) => (a.createdTime < b.createdTime ? 1 : -1))
      .forEach((crawl) => {
        if (crawl.taskId && !map.has(crawl.taskId)) map.set(crawl.taskId, crawl);
      });
    return map;
  }, [recentRuns.data]);

  const busyDatasourceIds = useMemo(() => {
    const set = new Set<string>();
    latestByTask.forEach((crawl) => {
      if (isRunActive(crawl.status)) set.add(crawl.datasourceId);
    });
    return set;
  }, [latestByTask]);

  const hasFilters =
    keyword.length > 0 || statusFilter !== undefined || datasourceFilter.length > 0 || onlyAbnormal;

  const activeTask = activeTaskId ? records.find((task) => task.id === activeTaskId) : undefined;

  useEffect(() => {
    // A changed page or filter set brings new rows; keeping ids that scrolled away would make the
    // batch bar act on invisible tasks.
    setSelected((current) => (current.size === 0 ? current : new Set()));
  }, [pageNo, keyword, statusFilter, datasourceFilter, onlyAbnormal]);

  const writeParams = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    if (resetPage) next.delete("pageNo");
    setSearchParams(next, { replace: true });
  };

  const resetFilters = () => writeParams({ q: "", status: "", ds: "", abnormal: "" });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tasks"] });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "task"] });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawls"] });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawl-summary"] });
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
      setDeleteTarget(null);
      setActiveTaskId(null);
      toast.success("任务已删除");
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
    onSuccess: ({ kind, total: count, failed }) => {
      invalidate();
      setConfirm(null);
      setSelected(new Set());
      if (failed === 0) {
        toast.success(kind === "offline" ? `已下线 ${count} 个任务` : `已删除 ${count} 个任务`);
      } else {
        toast.warning(`${count} 个任务中有 ${failed} 个未完成，请刷新后确认`);
      }
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "批量操作失败")),
  });

  const selectedTasks = records.filter((task) => selected.has(task.id));
  const onlineSelected = selectedTasks.filter((task) => task.status === "RUNNING");

  const requestDelete = (task: CrawlTask) => {
    if (task.status === "RUNNING") {
      toast.error("已上线的任务不能直接删除，请先下线");
      return;
    }
    setDeleteTarget(task);
  };

  const requestBatchDelete = () => {
    if (onlineSelected.length > 0) {
      toast.error("所选任务中包含已上线的任务，请先下线后再删除");
      return;
    }
    setConfirm({ kind: "delete", ids: selectedTasks.map((task) => task.id) });
  };

  // The abnormal shortcut is a server filter now, so its total needs no page arithmetic.
  const filterSummary = `${selected.size > 0 ? `已选 ${selected.size} 个 · ` : ""}${
    hasFilters ? `筛出 ${total} 个` : `共 ${total} 个采集任务`
  }`;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="元数据采集"
        description="哪些库还没接进来、上次谁失败了、这次结构变了什么。"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
        extra={
          <Button size="small" variant="primary" onClick={() => navigate(`${tasksPath}/new`)}>
            新建采集任务
          </Button>
        }
      />

      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-4 max-md:px-4">
        <HealthBand
          summary={summary.data ?? EMPTY_SUMMARY}
          onlyAbnormal={onlyAbnormal}
          onToggleAbnormal={() => writeParams({ abnormal: onlyAbnormal ? "" : "1" })}
        />

        {page.isError && (
          <Alert className="shrink-0">
            {dataMapFailureMessage(page.error, "采集任务加载失败，请稍后重试。")}
          </Alert>
        )}

        <Card className="flex min-h-0 flex-1 flex-col">
          <TaskTabs active="tasks" />

          <TaskFilters
            keyword={keyword}
            status={statusFilter ?? ""}
            datasourceId={datasourceFilter}
            datasources={datasources.options}
            hasFilters={hasFilters}
            summary={filterSummary}
            onKeywordChange={(value) => writeParams({ q: value })}
            onStatusChange={(value) => writeParams({ status: value })}
            onDatasourceChange={(value) => writeParams({ ds: value })}
            onReset={resetFilters}
          />

          <div className="min-h-0 flex-1 overflow-auto">
            {page.isPending ? (
              <div className="py-16 text-center text-sm text-[var(--yak-components-muted-text)]">
                正在加载采集任务…
              </div>
            ) : isEmpty && !hasFilters ? (
              <DataMapEmptyState
                title="还没有采集任务"
                description="一个任务对应一个库。选择数据源与调度方式后，平台会按配置把表结构同步到资产目录。"
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
            ) : records.length === 0 ? (
              <DataMapEmptyState
                title="没有符合条件的任务"
                description={
                  onlyAbnormal
                    ? "当前没有异常任务。任务最近一次采集已恢复正常。"
                    : "没有匹配搜索与筛选条件的任务。"
                }
                action={
                  <Button size="small" onClick={resetFilters}>
                    清除筛选
                  </Button>
                }
              />
            ) : (
              <TaskTable
                records={records}
                loading={page.isFetching && !page.isPlaceholderData}
                latestByTask={latestByTask}
                datasourceNames={datasourceNames}
                ownerNames={ownerNames}
                busyDatasourceIds={busyDatasourceIds}
                selected={selected}
                runningTaskId={run.isPending ? (run.variables ?? null) : null}
                onSelectedChange={setSelected}
                onOpen={setActiveTaskId}
                onRun={(task) => run.mutate(task.id)}
                onEdit={(task) => navigate(`${tasksPath}/${task.id}/edit`)}
                onToggleStatus={(task) =>
                  toggle.mutate({
                    taskId: task.id,
                    status: task.status === "RUNNING" ? "OFFLINE" : "RUNNING",
                  })
                }
                onDelete={requestDelete}
              />
            )}
          </div>

          {total > DEFAULT_PAGE_SIZE && (
            <div className="shrink-0 border-t border-[var(--yak-components-panel-border)] px-4 py-2.5">
              <Pagination
                page={pageNo}
                pageSize={DEFAULT_PAGE_SIZE}
                total={total}
                onChange={(nextPage) =>
                  writeParams({ pageNo: nextPage > 1 ? String(nextPage) : "" }, false)
                }
              />
            </div>
          )}
        </Card>
      </div>

      <TaskDrawer
        task={activeTask ?? null}
        open={Boolean(activeTask)}
        onOpenChange={(open) => {
          if (!open) setActiveTaskId(null);
        }}
        datasourceName={
          activeTask
            ? (datasourceNames.get(activeTask.datasourceId) ?? activeTask.datasourceId)
            : ""
        }
        ownerName={activeTask ? (ownerNames.get(activeTask.ownerId) ?? null) : null}
        running={run.isPending && run.variables === activeTask?.id}
        onRun={(task) => run.mutate(task.id)}
        onEdit={(task) => navigate(`${tasksPath}/${task.id}/edit`)}
      />

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

      <BatchConfirmDialog
        kind={confirm?.kind ?? null}
        count={confirm?.ids.length ?? 0}
        pending={batch.isPending}
        blocked={onlineSelected.length > 0}
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && batch.mutate(confirm)}
      />

      <DeleteTaskDialog
        task={deleteTarget}
        pending={remove.isPending}
        onClose={() => setDeleteTarget(null)}
        onConfirm={(taskId) => remove.mutate(taskId)}
      />
    </div>
  );
}
