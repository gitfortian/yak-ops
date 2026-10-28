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

export const CRAWL_STATUS_LABEL: Record<CrawlStatus, string> = {
  PENDING: "排队中",
  RUNNING: "采集中",
  SUCCEEDED: "已完成",
  FAILED: "失败",
  INTERRUPTED: "异常中断",
};

export const CRAWL_STATUS_TONE: Record<CrawlStatus, NonNullable<BadgeProps["tone"]>> = {
  PENDING: "neutral",
  RUNNING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  INTERRUPTED: "neutral",
};

export const CRAWL_TRIGGER_LABEL: Record<CrawlTrigger, string> = {
  MANUAL: "手动",
  SCHEDULED: "定时",
};

export const TASK_STATUS_LABEL: Record<CrawlTaskStatus, string> = {
  DRAFT: "草稿",
  RUNNING: "运行中",
  OFFLINE: "已下线",
};

export const TASK_STATUS_TONE: Record<CrawlTaskStatus, NonNullable<BadgeProps["tone"]>> = {
  DRAFT: "neutral",
  RUNNING: "info",
  OFFLINE: "neutral",
};

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
