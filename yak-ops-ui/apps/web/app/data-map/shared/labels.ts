import { parseCron, type BadgeProps } from "@yak-ops/yak-ui";

import type {
  CrawlScheduleType,
  CrawlStatus,
  CrawlTaskStatus,
  CrawlTrigger,
  TableStatus,
} from "@/service/data-map/types";

export const TABLE_STATUS_LABEL: Record<TableStatus, string> = {
  ACTIVE: "正常",
  MISSING: "已缺失",
};

export const TABLE_STATUS_TONE: Record<TableStatus, NonNullable<BadgeProps["tone"]>> = {
  ACTIVE: "success",
  MISSING: "warning",
};

/**
 * What a run looks like in the UI. `PARTIAL` is derived, never stored: the backend reports such a
 * run as `SUCCEEDED` with `failedCount > 0`.
 */
export type RunDisplayStatus = CrawlStatus | "PARTIAL";

export const RUN_STATUS_LABEL: Record<RunDisplayStatus, string> = {
  PENDING: "排队中",
  RUNNING: "采集中",
  SUCCEEDED: "成功",
  PARTIAL: "部分成功",
  FAILED: "失败",
  INTERRUPTED: "异常中断",
};

export const RUN_STATUS_TONE: Record<RunDisplayStatus, NonNullable<BadgeProps["tone"]>> = {
  PENDING: "neutral",
  RUNNING: "info",
  SUCCEEDED: "success",
  PARTIAL: "warning",
  FAILED: "danger",
  INTERRUPTED: "neutral",
};

/**
 * A run that ends with failed tables makes no missing-table judgement, so it must never be shown
 * as a plain success. Keep this derivation in one place rather than in each surface.
 */
export function runDisplayStatus(
  status: CrawlStatus | null | undefined,
  failedCount?: number | null,
): RunDisplayStatus | null {
  if (!status) return null;
  return status === "SUCCEEDED" && (failedCount ?? 0) > 0 ? "PARTIAL" : status;
}

export const CRAWL_TRIGGER_LABEL: Record<CrawlTrigger, string> = {
  MANUAL: "手动",
  SCHEDULED: "定时",
};

/**
 * `RUNNING` on a task means "scheduled and running by the scheduler", not "an instance is running
 * right now" — that state lives on the run. The label says so; the enum stays untouched.
 */
export const TASK_STATUS_LABEL: Record<CrawlTaskStatus, string> = {
  DRAFT: "草稿",
  RUNNING: "已上线",
  OFFLINE: "已下线",
};

export const TASK_STATUS_TONE: Record<CrawlTaskStatus, NonNullable<BadgeProps["tone"]>> = {
  DRAFT: "neutral",
  RUNNING: "info",
  OFFLINE: "neutral",
};

/**
 * Reads the task status filter from the URL. An unknown value is dropped rather than sent to the
 * API, because the list endpoint rejects a status outside the enum.
 */
const TASK_STATUSES: CrawlTaskStatus[] = ["DRAFT", "RUNNING", "OFFLINE"];

export const readTaskStatus = (value: string | null | undefined): CrawlTaskStatus | undefined =>
  TASK_STATUSES.find((status) => status === value);

const WEEKDAY_LABEL: Record<number, string> = {
  1: "日",
  2: "一",
  3: "二",
  4: "三",
  5: "四",
  6: "五",
  7: "六",
};

const pad2 = (value: number) => String(value).padStart(2, "0");

/** Human summary of a stored schedule; unsupported expressions stay visible as raw Cron. */
export function scheduleLabel(scheduleType: CrawlScheduleType, cronExpression: string | null) {
  if (scheduleType === "MANUAL") return "手动执行";
  if (!cronExpression) return "未配置调度";
  const parsed = parseCron(cronExpression);
  if (!parsed) return `Cron ${cronExpression}`;
  switch (parsed.period) {
    case "minute":
      return `每 ${parsed.minuteInterval} 分钟`;
    case "hour":
      return parsed.hourMode === "specified"
        ? `指定小时 · ${pad2(parsed.specifiedHourMinute)} 分`
        : `每 ${parsed.hourInterval} 小时`;
    case "day":
      return `每天 ${parsed.dayTime}`;
    case "week":
      return `每周${parsed.weekdays.map((day) => WEEKDAY_LABEL[day]).join("、")} ${parsed.weekTime}`;
    case "month":
      return `每月 · ${parsed.monthTime}`;
    case "year":
      return `每年 · ${parsed.yearTime}`;
  }
}

/** Renders a wall-clock duration compactly: 45秒, 1分20秒, 1小时2分. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || seconds < 0) return "-";
  if (seconds < 60) return `${seconds}秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  if (minutes < 60) return rest === 0 ? `${minutes}分` : `${minutes}分${rest}秒`;
  const hours = Math.floor(minutes / 60);
  const left = minutes % 60;
  return left === 0 ? `${hours}小时` : `${hours}小时${left}分`;
}

/** Parses either the stored `yyyy-MM-dd HH:mm:ss` form or an ISO string; null when unparseable. */
const parseTimestamp = (raw: string | null | undefined): Date | null => {
  if (!raw) return null;
  const date = new Date(raw.includes("T") ? raw : raw.replace(" ", "T"));
  return Number.isNaN(date.getTime()) ? null : date;
};

/** Absolute wall clock without seconds: 10-30 12:02. */
export function clockLabel(raw: string | null | undefined): string {
  const date = parseTimestamp(raw);
  if (!date) return raw ?? "-";
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

/**
 * Age of a timestamp, e.g. 12 分钟前. Beyond a month the absolute date is more useful than a count
 * of days, so the two forms share one column instead of two.
 */
export function relativeTime(raw: string | null | undefined, now: number = Date.now()): string {
  if (!raw) return "-";
  const date = parseTimestamp(raw);
  if (!date) return raw;
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000));
  if (seconds < 60) return "刚刚";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return clockLabel(raw);
}

export const CHANGE_TYPE_LABEL: Record<string, string> = {
  COLUMN_ADDED: "新增字段",
  COLUMN_REMOVED: "删除字段",
  COLUMN_TYPE_CHANGED: "字段类型变更",
  COLUMN_NULLABILITY_CHANGED: "字段可空性变更",
  PRIMARY_KEY_CHANGED: "主键变更",
  TABLE_COMMENT_CHANGED: "表注释变更",
};

/** An asset is addressable, so its detail page is a route rather than a dialog. */
export const tablePath = (tableId: string) =>
  `/data-governance/data-map/tables/${encodeURIComponent(tableId)}`;

export const DATA_MAP_BASE = "/data-governance/data-map";

export const locationLabel = (database: string, schema: string) => {
  const parts = [database, schema].filter((part) => part.length > 0);
  return parts.length > 0 ? parts.join(" / ") : "-";
};
