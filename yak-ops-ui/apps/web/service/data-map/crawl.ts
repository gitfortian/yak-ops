import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type { Crawl, CrawlDetail, CrawlQuery, CrawlSummary, Paging } from "./types";

const API = "/api/v1/data-map";

export const listCrawls = (query: CrawlQuery, signal?: AbortSignal) => {
  const search = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.datasourceId) search.set("datasourceId", query.datasourceId);
  if (query.taskId) search.set("taskId", query.taskId);
  if (query.status) search.set("status", query.status);
  if (query.triggerType) search.set("triggerType", query.triggerType);
  if (query.failed !== undefined) search.set("failed", String(query.failed));
  if (query.days) search.set("days", String(query.days));
  return dataMapRequest(
    HttpUtils.getData<Paging<Crawl>>(`${API}/crawls?${search.toString()}`, {
      signal,
      skipErrorHandler: true,
    }),
  );
};

export const getCrawl = (crawlId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<CrawlDetail>(`${API}/crawls/${encodeURIComponent(crawlId)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

/** Workspace-wide counts for the health band; `hours` widens the run window beyond one day. */
export const getCrawlSummary = (hours: number, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<CrawlSummary>(`${API}/crawls/summary?hours=${hours}`, {
      signal,
      skipErrorHandler: true,
    }),
  );
