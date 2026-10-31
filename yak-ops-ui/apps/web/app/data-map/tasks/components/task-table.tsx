import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { MoreHorizontal, Play } from "lucide-react";

import type { Crawl, CrawlTask } from "@/service/data-map/types";

import {
  clockLabel,
  formatDuration,
  locationLabel,
  relativeTime,
  runDisplayStatus,
  scheduleLabel,
} from "../../shared/labels";
import { RunBadge, RunNotStarted, TaskLifecycle } from "./status";

interface Props {
  records: CrawlTask[];
  loading: boolean;
  /** Newest instance per task; a task without an entry has never run or fell outside the window. */
  latestByTask: Map<string, Crawl>;
  datasourceNames: Map<string, string>;
  ownerNames: Map<string, string>;
  /** Datasources with a queued or running instance: the backend allows one active run per source. */
  busyDatasourceIds: Set<string>;
  selected: Set<string>;
  runningTaskId: string | null;
  onSelectedChange: (ids: Set<string>) => void;
  onOpen: (taskId: string) => void;
  onRun: (task: CrawlTask) => void;
  onEdit: (task: CrawlTask) => void;
  onToggleStatus: (task: CrawlTask) => void;
  onDelete: (task: CrawlTask) => void;
}

const isRunActive = (status?: string | null) => status === "PENDING" || status === "RUNNING";

/**
 * Widths are the 23 / 17 / 12 / 9 / 21 / 8 / 10 split documented in the proposal, expressed in
 * pixels because the table layout engine only resolves px; the task column stays flexible.
 */
const COLUMN_WIDTH = {
  range: 180,
  schedule: 130,
  lifecycle: 96,
  lastRun: 220,
  added: 80,
  actions: 100,
} as const;

export default function TaskTable({
  records,
  loading,
  latestByTask,
  datasourceNames,
  ownerNames,
  busyDatasourceIds,
  selected,
  runningTaskId,
  onSelectedChange,
  onOpen,
  onRun,
  onEdit,
  onToggleStatus,
  onDelete,
}: Props) {
  const columns: TableColumns<CrawlTask> = [
    {
      key: "name",
      title: "任务",
      minWidth: 240,
      render: (_value, task) => {
        const latest = latestByTask.get(task.id);
        const shown = runDisplayStatus(task.lastRunStatus, latest?.failedCount);
        const abnormal = shown === "FAILED" || shown === "PARTIAL";
        const owner = ownerNames.get(task.ownerId);
        return (
          <div className="flex min-w-0 items-stretch gap-2">
            <span
              aria-hidden="true"
              className={`w-0.5 shrink-0 rounded-full ${
                abnormal ? "bg-[var(--yak-components-badge-danger-text)]" : "bg-transparent"
              }`}
            />
            <div className="min-w-0">
              <div
                className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]"
                title={task.name}
              >
                {task.name}
              </div>
              <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
                {owner ?? "未指定负责人"} · {relativeTime(task.updatedTime)}更新
              </div>
            </div>
          </div>
        );
      },
    },
    {
      key: "range",
      title: "采集范围",
      width: COLUMN_WIDTH.range,
      render: (_value, task) => (
        <div className="min-w-0">
          <div className="truncate text-xs text-[var(--yak-components-panel-text)]">
            {datasourceNames.get(task.datasourceId) ?? task.datasourceId}
          </div>
          <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
            {locationLabel(task.database, task.schema)}
          </div>
        </div>
      ),
    },
    {
      key: "schedule",
      title: "调度",
      width: COLUMN_WIDTH.schedule,
      render: (_value, task) => {
        const next =
          task.status === "RUNNING"
            ? task.nextFireTime
              ? `下次 ${clockLabel(task.nextFireTime)}`
              : "等待调度"
            : task.status === "DRAFT"
              ? "上线后才会执行"
              : "已停止";
        return (
          <div className="min-w-0">
            <div className="truncate text-xs text-[var(--yak-components-panel-text)]">
              {scheduleLabel(task.scheduleType, task.cronExpression)}
            </div>
            <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
              {next}
            </div>
          </div>
        );
      },
    },
    {
      key: "lifecycle",
      title: "上线",
      width: COLUMN_WIDTH.lifecycle,
      render: (_value, task) => <TaskLifecycle status={task.status} />,
    },
    {
      key: "lastRun",
      title: "最近采集",
      width: COLUMN_WIDTH.lastRun,
      render: (_value, task) => {
        const latest = latestByTask.get(task.id);
        const shown = runDisplayStatus(task.lastRunStatus, latest?.failedCount);
        if (!shown) return <RunNotStarted />;

        const when = relativeTime(latest?.createdTime ?? task.lastRunTime);
        let detail = "";
        if (shown === "PARTIAL") detail = ` · ${latest?.failedCount ?? 0} 张表失败`;
        else if (shown === "SUCCEEDED") detail = ` · ${formatDuration(latest?.durationSeconds)}`;
        else if (shown === "FAILED") detail = latest?.error ? " · 实例失败" : "";

        return (
          <div className="min-w-0">
            <RunBadge status={shown} />
            <div
              className="mt-1 truncate text-xs text-[var(--yak-components-muted-text)]"
              title={latest?.error ?? undefined}
            >
              {when}
              {detail}
            </div>
          </div>
        );
      },
    },
    {
      key: "added",
      title: "新增",
      width: COLUMN_WIDTH.added,
      align: "right",
      render: (_value, task) => {
        const latest = latestByTask.get(task.id);
        if (!latest || latest.status === "FAILED" || latest.status === "RUNNING") {
          return <span className="text-xs text-[var(--yak-components-muted-text)]">—</span>;
        }
        return (
          <span className="text-xs tabular-nums text-[var(--yak-components-panel-text)]">
            {latest.addedCount > 0 ? `+${latest.addedCount}` : "0"}
          </span>
        );
      },
    },
    {
      key: "actions",
      title: "",
      width: COLUMN_WIDTH.actions,
      align: "right",
      render: (_value, task) => {
        const latest = latestByTask.get(task.id);
        const active = isRunActive(latest?.status);
        const blockedBySource = busyDatasourceIds.has(task.datasourceId);
        const online = task.status === "RUNNING";
        const canRun = online && !blockedBySource && runningTaskId !== task.id;
        const runLabel = runningTaskId === task.id ? "提交中" : active ? "采集中" : "采集";
        const runHint = !online
          ? "任务未上线，不能采集"
          : blockedBySource
            ? "该数据源正在采集，同一数据源同时只允许一个实例"
            : "";

        return (
          <div className="flex items-center justify-end gap-1">
            <Button
              size="small"
              variant="ghost"
              className="h-7 px-2 text-xs text-[var(--yak-color-primary)]"
              disabled={!canRun}
              title={runHint || runLabel}
              onClick={() => onRun(task)}
            >
              <Play size={11} aria-hidden="true" className="mr-1" />
              {runLabel}
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger
                aria-label={`${task.name} 更多操作`}
                className="grid size-7 cursor-pointer place-items-center rounded-md text-[var(--yak-components-muted-text)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)] focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary-ring)]"
              >
                <MoreHorizontal size={14} />
              </DropdownMenuTrigger>
              <DropdownMenuContent side="bottom" align="end">
                <DropdownMenuItem onClick={() => onOpen(task.id)}>查看详情</DropdownMenuItem>
                <DropdownMenuItem onClick={() => onEdit(task)}>编辑配置</DropdownMenuItem>
                <DropdownMenuItem onClick={() => onToggleStatus(task)}>
                  {online ? "下线" : "上线"}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  tone="danger"
                  disabled={online || active}
                  onClick={() => onDelete(task)}
                >
                  {online ? "需先下线才能删除" : "删除任务"}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        );
      },
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={records}
      rowKey="id"
      size="small"
      loading={loading}
      rowHoverable
      pagination={false}
      scroll={{ x: 1048 }}
      rowSelection={{
        selectedRowKeys: [...selected],
        onChange: (keys) => onSelectedChange(new Set(keys.map(String))),
      }}
      onRow={(task) => ({
        className: "cursor-pointer",
        onClick: (event) => {
          const target = event.target as HTMLElement;
          if (target.closest("button,input,a,[role='menuitem']")) return;
          onOpen(task.id);
        },
      })}
    />
  );
}
