import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type { Crawl, CrawlDetail, Paging } from "./types";

const API = "/api/v1/data-map";

export const listCrawls = (
  query: { datasourceId?: string; taskId?: string; pageNo: number; pageSize: number },
  signal?: AbortSignal,
) => {
  const search = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  if (query.datasourceId) search.set("datasourceId", query.datasourceId);
  if (query.taskId) search.set("taskId", query.taskId);
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
