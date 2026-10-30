import {
  Badge,
  Button,
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerTitle,
  Skeleton,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, Pencil, Play } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";

import { searchTables } from "@/service/data-map/catalog";
import { getCrawl, listCrawls } from "@/service/data-map/crawl";
import type { Crawl, CrawlTask } from "@/service/data-map/types";

import { dataMapKeys } from "../../shared/keys";
import {
  clockLabel,
  DATA_MAP_BASE,
  formatDuration,
  locationLabel,
  relativeTime,
  runDisplayStatus,
  scheduleLabel,
} from "../../shared/labels";
import { useDataMapScope } from "../../shared/scope";
import { RunBadge, RunNotStarted, TaskLifecycle } from "./status";

const RECENT_RUN_LIMIT = 8;
const VISIBLE_FAILURE_LIMIT = 50;

interface Props {
  task: CrawlTask | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  datasourceName: string;
  ownerName: string | null;
  running: boolean;
  onRun: (task: CrawlTask) => void;
  onEdit: (task: CrawlTask) => void;
}

function Section({
  title,
  extra,
  children,
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] p-3">
      <div className="mb-2 flex items-baseline justify-between gap-3">
        <h3 className="text-xs font-medium text-[var(--yak-components-panel-text)]">{title}</h3>
        {extra}
      </div>
      {children}
    </section>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-1">
      <span className="shrink-0 text-xs text-[var(--yak-components-muted-text)]">{label}</span>
      <span className="min-w-0 truncate text-right text-xs text-[var(--yak-components-panel-text)]">
        {children}
      </span>
    </div>
  );
}

function Elapsed({ crawl }: { crawl: Crawl }) {
  if (crawl.status !== "RUNNING") return null;
  const started = crawl.startedTime ? new Date(crawl.startedTime.replace(" ", "T")).getTime() : null;
  if (started === null || Number.isNaN(started)) return null;
  const seconds = Math.max(0, Math.round((Date.now() - started) / 1000));
  return <span> · 已运行 {formatDuration(seconds)}</span>;
}

/** Run counts read 新增 / 更新 / 缺失 / 失败 in that order everywhere; keep one renderer. */
function RunCounts({ crawl }: { crawl: Crawl }) {
  const item = (label: string, value: ReactNode, tone?: string) => (
    <div className="min-w-0">
      <div className="text-[11px] text-[var(--yak-components-muted-text)]">{label}</div>
      <div className={`mt-0.5 text-sm tabular-nums ${tone ?? "text-[var(--yak-components-panel-text)]"}`}>
        {value}
      </div>
    </div>
  );
  return (
    <div className="grid grid-cols-4 gap-2">
      {item("新增", `+${crawl.addedCount}`, "text-[var(--yak-components-badge-success-text)]")}
      {item("更新", crawl.updatedCount)}
      {item("缺失", crawl.removedCount)}
      {item(
        "失败",
        crawl.failedCount,
        crawl.failedCount > 0
          ? "text-[var(--yak-components-badge-danger-text)]"
          : "text-[var(--yak-components-muted-text)]",
      )}
    </div>
  );
}

/** Failures are fetched on demand: the list only needs the count, the detail carries the reasons. */
function RunFailures({ crawlId, failedCount }: { crawlId: string; failedCount: number }) {
  const { scope, ready } = useDataMapScope();
  const query = useQuery({
    queryKey: dataMapKeys.crawl(scope, crawlId),
    queryFn: ({ signal }) => getCrawl(crawlId, signal),
    enabled: ready,
    staleTime: 60 * 1000,
  });

  if (query.isPending) {
    return (
      <div className="mt-2 space-y-1.5 rounded-md bg-[var(--yak-color-surface-muted)] p-2.5">
        <Skeleton className="h-3 w-2/3" />
        <Skeleton className="h-3 w-1/2" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <div className="mt-2 rounded-md bg-[var(--yak-color-surface-muted)] p-2.5 text-xs text-[var(--yak-components-badge-danger-text)]">
        失败明细加载失败，稍后重试。
      </div>
    );
  }

  const failures = query.data?.failures ?? [];
  const visible = failures.slice(0, VISIBLE_FAILURE_LIMIT);
  return (
    <div className="mt-2 rounded-md bg-[var(--yak-color-surface-muted)] p-2.5">
      <div className="mb-1.5 text-[11px] text-[var(--yak-components-muted-text)]">
        本次不做缺失判定：失败表的存活状态保持不变。仅显示前 {VISIBLE_FAILURE_LIMIT} 张。
      </div>
      {visible.length === 0 ? (
        <div className="text-xs text-[var(--yak-components-muted-text)]">
          实例已记录 {failedCount} 张失败表，但明细为空。
        </div>
      ) : (
        <ul className="max-h-56 space-y-1.5 overflow-y-auto">
          {visible.map((failure) => (
            <li key={`${failure.tableName}-${failure.createdTime}`} className="text-xs">
              <div className="truncate font-medium text-[var(--yak-components-panel-text)]">
                {failure.tableName}
              </div>
              <div className="mt-0.5 break-words text-[11px] text-[var(--yak-components-badge-danger-text)]">
                {failure.error}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RunRow({ crawl }: { crawl: Crawl }) {
  const [expanded, setExpanded] = useState(false);
  const shown = runDisplayStatus(crawl.status, crawl.failedCount);
  if (!shown) return null;
  const expandable = shown === "PARTIAL";

  return (
    <li className="border-b border-[var(--yak-components-control-border)] py-2.5 last:border-b-0">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <RunBadge status={shown} />
            <span className="text-xs text-[var(--yak-components-panel-text)]">
              {clockLabel(crawl.createdTime)}
            </span>
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">
              {crawl.triggerType === "MANUAL" ? "手动" : "定时"}
              {crawl.retryOf ? " · 重试" : ""}
            </span>
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">
              {relativeTime(crawl.createdTime)}
              {crawl.durationSeconds != null ? ` · ${formatDuration(crawl.durationSeconds)}` : ""}
              <Elapsed crawl={crawl} />
            </span>
          </div>
          <div className="mt-1 text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">
            +{crawl.addedCount} / ~{crawl.updatedCount} / -{crawl.removedCount} /{" "}
            <span
              className={
                crawl.failedCount > 0 ? "text-[var(--yak-components-badge-danger-text)]" : ""
              }
            >
              {crawl.failedCount}
            </span>
          </div>
          {crawl.status === "FAILED" && crawl.error && (
            <div className="mt-1 break-words text-[11px] text-[var(--yak-components-badge-danger-text)]">
              {crawl.error}
            </div>
          )}
        </div>
        {expandable && (
          <button
            type="button"
            className="flex shrink-0 cursor-pointer items-center gap-0.5 rounded-md px-1.5 py-0.5 text-[11px] text-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)]"
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            失败表 {crawl.failedCount}
            <ChevronDown
              size={12}
              aria-hidden="true"
              className={`transition-transform ${expanded ? "rotate-180" : ""}`}
            />
          </button>
        )}
      </div>
      {expanded && expandable && (
        <RunFailures crawlId={crawl.id} failedCount={crawl.failedCount} />
      )}
    </li>
  );
}

/** Quality and lineage have no backend yet; the tab states the dependency instead of faking a score. */
function GovernancePlaceholder() {
  return (
    <div className="space-y-3">
      <div className="rounded-[var(--yak-radius-control-medium)] border border-dashed border-[var(--yak-components-control-border)] p-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-[var(--yak-components-panel-text)]">
            采集健康度
          </span>
          <Badge tone="warning">示例</Badge>
        </div>
        <p className="mt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          依赖数据质量（FYOIP-16），接入前不展示任何数值。
        </p>
      </div>
      <div className="rounded-[var(--yak-radius-control-medium)] border border-dashed border-[var(--yak-components-control-border)] p-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-[var(--yak-components-panel-text)]">血缘关系</span>
          <Badge tone="warning">示例</Badge>
        </div>
        <p className="mt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          依赖资产关系（FYOIP-17），接入前不提供入口。
        </p>
      </div>
    </div>
  );
}

export default function TaskDrawer({
  task,
  open,
  onOpenChange,
  datasourceName,
  ownerName,
  running,
  onRun,
  onEdit,
}: Props) {
  const { scope, ready } = useDataMapScope();
  const [tab, setTab] = useState("overview");

  const runs = useQuery({
    queryKey: dataMapKeys.taskCrawls(scope, task?.id ?? "", RECENT_RUN_LIMIT),
    queryFn: ({ signal }) =>
      listCrawls({ taskId: task!.id, pageNo: 1, pageSize: RECENT_RUN_LIMIT }, signal),
    enabled: ready && open && Boolean(task),
    staleTime: 15 * 1000,
  });

  // How many assets this task actually owns, so the overview can link into the catalog view of
  // exactly that scope instead of sending the user to filter by hand.
  const scopeAssets = useQuery({
    queryKey: dataMapKeys.tables(scope, {
      datasourceId: task?.datasourceId ?? "",
      database: task?.database ?? "",
      pageNo: 1,
      pageSize: 1,
    }),
    queryFn: ({ signal }) =>
      searchTables(
        {
          datasourceId: task!.datasourceId,
          database: task!.database,
          pageNo: 1,
          pageSize: 1,
        },
        signal,
      ),
    enabled: ready && open && Boolean(task),
    staleTime: 60 * 1000,
  });

  const records = useMemo(
    () =>
      [...(runs.data?.bizData ?? [])].sort((a, b) => (a.createdTime < b.createdTime ? 1 : -1)),
    [runs.data],
  );
  const latest = records[0];
  const shownRun = latest ? runDisplayStatus(latest.status, latest.failedCount) : null;

  const runButton = task && (
    <Button
      size="small"
      variant="primary"
      className="h-7 text-xs"
      disabled={task.status !== "RUNNING" || running}
      onClick={() => onRun(task)}
    >
      <Play size={12} aria-hidden="true" className="mr-1" />
      {running ? "提交中" : "立即采集"}
    </Button>
  );

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent width={560}>
        {task && (
          <>
            <header className="shrink-0 border-b border-[var(--yak-components-panel-border)] px-4 py-3">
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <DrawerTitle className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]">
                      {task.name}
                    </DrawerTitle>
                    <TaskLifecycle status={task.status} />
                  </div>
                  <DrawerDescription className="mt-1 truncate text-xs text-[var(--yak-components-muted-text)]">
                    {datasourceName} · {locationLabel(task.database, task.schema)} ·{" "}
                    {ownerName ?? "未指定负责人"}
                  </DrawerDescription>
                </div>
              </div>
              <div className="mt-2.5 flex items-center gap-2">
                {runButton}
                <Button
                  size="small"
                  className="h-7 text-xs"
                  disabled={task.status === "RUNNING" && latest?.status === "RUNNING"}
                  onClick={() => onEdit(task)}
                >
                  <Pencil size={12} aria-hidden="true" className="mr-1" />
                  编辑配置
                </Button>
                {task.status !== "RUNNING" && (
                  <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                    未上线，无法采集
                  </span>
                )}
              </div>
            </header>

            <Tabs
              value={tab}
              onValueChange={(value) => setTab(value as string)}
              className="flex min-h-0 flex-1 flex-col"
            >
              <TabsList className="shrink-0 px-4 pt-1">
                <TabsTab value="overview">概览</TabsTab>
                <TabsTab value="runs">运行历史</TabsTab>
                <TabsTab value="governance" className="gap-1">
                  质量与血缘
                  <span className="ml-1 text-[10px] text-[var(--yak-components-badge-warning-text)]">
                    示例
                  </span>
                </TabsTab>
              </TabsList>

              <DrawerBody className="space-y-3 pt-3">
                <TabsPanel value="overview" className="space-y-3">
                  <Section
                    title="本次采集"
                    extra={
                      latest ? (
                        <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                          {relativeTime(latest.createdTime)}
                        </span>
                      ) : null
                    }
                  >
                    {runs.isPending ? (
                      <Skeleton className="h-12 w-full" />
                    ) : !latest ? (
                      <div className="flex items-center gap-2">
                        <RunNotStarted />
                        <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                          上线后按调度执行
                        </span>
                      </div>
                    ) : (
                      <>
                        <div className="mb-2 flex items-center gap-2">
                          {shownRun && <RunBadge status={shownRun} />}
                          <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                            {latest.triggerType === "MANUAL" ? "手动" : "定时"}
                            {latest.durationSeconds != null
                              ? ` · ${formatDuration(latest.durationSeconds)}`
                              : ""}
                          </span>
                        </div>
                        <RunCounts crawl={latest} />
                        {shownRun === "PARTIAL" && (
                          <p className="mt-2 text-[11px] leading-4 text-[var(--yak-components-badge-warning-text)]">
                            本次不做缺失判定：{latest.failedCount} 张失败表的存活状态保持不变。
                          </p>
                        )}
                        {latest.status === "FAILED" && latest.error && (
                          <p className="mt-2 break-words text-[11px] leading-4 text-[var(--yak-components-badge-danger-text)]">
                            {latest.error}
                          </p>
                        )}
                      </>
                    )}
                  </Section>

                  <div className="grid grid-cols-2 gap-3">
                    <Section
                      title="数据源"
                      extra={
                        <Link
                          className="text-[11px] text-[var(--yak-color-primary)] hover:underline"
                          to={`${DATA_MAP_BASE}/catalog?datasourceId=${encodeURIComponent(task.datasourceId)}&database=${encodeURIComponent(task.database)}`}
                        >
                          查看资产
                        </Link>
                      }
                    >
                      <Field label="数据源">{datasourceName}</Field>
                      <Field label="库 / Schema">{locationLabel(task.database, task.schema)}</Field>
                      <Field label="该范围资产">
                        {scopeAssets.isPending ? "…" : (scopeAssets.data?.pagination.total ?? 0)} 张
                      </Field>
                      <Field label="最近同步">
                        {latest ? relativeTime(latest.createdTime) : "未采集"}
                      </Field>
                    </Section>
                    <Section title="调度">
                      <Field label="方式">
                        {scheduleLabel(task.scheduleType, task.cronExpression)}
                      </Field>
                      <Field label="下次运行">
                        {task.status === "RUNNING"
                          ? task.nextFireTime
                            ? clockLabel(task.nextFireTime)
                            : "等待调度"
                          : "已停止"}
                      </Field>
                      <Field label="最近更新">{clockLabel(task.updatedTime)}</Field>
                    </Section>
                  </div>

                  <Section title="排除规则" extra={<span className="text-[11px] text-[var(--yak-components-muted-text)]">已排除 {task.excludeTables.length} 张</span>}>
                    {task.excludePattern ? (
                      <code className="block break-all rounded-md bg-[var(--yak-color-surface-muted)] px-2 py-1.5 text-[11px] text-[var(--yak-components-panel-text)]">
                        {task.excludePattern}
                      </code>
                    ) : (
                      <div className="text-xs text-[var(--yak-components-muted-text)]">
                        未配置正则排除
                      </div>
                    )}
                    <p className="mt-2 text-[11px] leading-4 text-[var(--yak-components-muted-text)]">
                      被排除的表不刷新字段，也不判缺失。
                    </p>
                  </Section>
                </TabsPanel>

                <TabsPanel value="runs">
                  {runs.isPending ? (
                    <div className="space-y-2">
                      <Skeleton className="h-14 w-full" />
                      <Skeleton className="h-14 w-full" />
                      <Skeleton className="h-14 w-full" />
                    </div>
                  ) : records.length === 0 ? (
                    <div className="rounded-[var(--yak-radius-control-medium)] border border-dashed border-[var(--yak-components-control-border)] p-4 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                      还没有采集实例。上线后按调度执行，或使用「立即采集」。
                    </div>
                  ) : (
                    <ul>
                      {records.map((crawl) => (
                        <RunRow key={crawl.id} crawl={crawl} />
                      ))}
                    </ul>
                  )}
                </TabsPanel>

                <TabsPanel value="governance">
                  <GovernancePlaceholder />
                </TabsPanel>
              </DrawerBody>
            </Tabs>
          </>
        )}
      </DrawerContent>
    </Drawer>
  );
}
