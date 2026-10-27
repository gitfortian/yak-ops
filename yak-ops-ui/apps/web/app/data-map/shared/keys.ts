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
  tags: (scope: DataMapScope) => [...dataMapKeys.all, "tags", ...scope] as const,
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
  crawls: (scope: DataMapScope, datasourceId: string, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "crawls", ...scope, datasourceId, pageNo, pageSize] as const,
  crawl: (scope: DataMapScope, crawlId: string) =>
    [...dataMapKeys.all, "crawl", ...scope, crawlId] as const,
  favorites: (scope: DataMapScope, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "favorites", ...scope, pageNo, pageSize] as const,
  recent: (scope: DataMapScope, pageNo: number, pageSize: number) =>
    [...dataMapKeys.all, "recent", ...scope, pageNo, pageSize] as const,
};
