import type { TableQuery } from "@/service/data-map/types";

import type { DataMapScope } from "./scope";

/**
 * Cache keys of the data map. Shared lookups keep one entry across routes; per-route reads carry
 * the exact query object so a changed filter cannot reuse the previous page.
 */
export const dataMapKeys = {
  all: ["data-map"] as const,
  taxonomy: (scope: DataMapScope) => [...dataMapKeys.all, "taxonomy", ...scope] as const,
  datasources: (scope: DataMapScope) => [...dataMapKeys.all, "datasources", ...scope] as const,
  tagDictionary: (scope: DataMapScope) => [...dataMapKeys.all, "tag-dictionary", ...scope] as const,
  owners: (scope: DataMapScope) => [...dataMapKeys.all, "owners", ...scope] as const,
  overview: (scope: DataMapScope) => [...dataMapKeys.all, "overview", ...scope] as const,
  tables: (scope: DataMapScope, query: TableQuery) =>
    [...dataMapKeys.all, "tables", ...scope, query] as const,
  table: (scope: DataMapScope, tableId: string) =>
    [...dataMapKeys.all, "table", ...scope, tableId] as const,
  changes: (scope: DataMapScope, tableId: string) =>
    [...dataMapKeys.all, "changes", ...scope, tableId] as const,
  preview: (scope: DataMapScope, tableId: string) =>
    [...dataMapKeys.all, "preview", ...scope, tableId] as const,
  tasks: (scope: DataMapScope, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "tasks", ...scope, pageNo, pageSize] as const,
  task: (scope: DataMapScope, taskId: string) =>
    [...dataMapKeys.all, "task", ...scope, taskId] as const,
  taskSchedulePreview: (scope: DataMapScope, cronExpression: string) =>
    [...dataMapKeys.all, "task-schedule-preview", ...scope, cronExpression] as const,
  tableOptions: (
    scope: DataMapScope,
    datasourceId: string,
    database: string,
    schema: string,
    keyword: string,
  ) =>
    [
      ...dataMapKeys.all,
      "table-options",
      ...scope,
      datasourceId,
      database,
      schema,
      keyword,
    ] as const,
  excludePreview: (
    scope: DataMapScope,
    datasourceId: string,
    database: string,
    schema: string,
    regex: string,
  ) =>
    [
      ...dataMapKeys.all,
      "exclude-preview",
      ...scope,
      datasourceId,
      database,
      schema,
      regex,
    ] as const,
  crawls: (
    scope: DataMapScope,
    datasourceId: string,
    taskId: string,
    pageNo: number,
    pageSize: number,
  ) => [...dataMapKeys.all, "crawls", ...scope, datasourceId, taskId, pageNo, pageSize] as const,
  crawl: (scope: DataMapScope, crawlId: string) =>
    [...dataMapKeys.all, "crawl", ...scope, crawlId] as const,
  favorites: (scope: DataMapScope, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "favorites", ...scope, pageNo, pageSize] as const,
  recent: (scope: DataMapScope, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "recent", ...scope, pageNo, pageSize] as const,
};
