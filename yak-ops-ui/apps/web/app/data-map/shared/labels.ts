import type { BadgeProps } from "@yak-ops/yak-ui";

import type { CrawlStatus, TableStatus } from "@/service/data-map/types";

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
};

export const CRAWL_STATUS_TONE: Record<CrawlStatus, NonNullable<BadgeProps["tone"]>> = {
  PENDING: "neutral",
  RUNNING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
};

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
