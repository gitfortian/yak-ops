import HttpUtils from "@/service/http/HttpUtils";

import { dataMapRequest } from "./request";
import type {
  CrawlExcludePreview,
  CrawlSchedulePreview,
  Crawl,
  CrawlTask,
  CrawlTaskSave,
  CrawlTaskStatus,
  Paging,
} from "./types";

const API = "/api/v1/data-map";

export const listTasks = (
  query: { pageNo: number; pageSize: number },
  signal?: AbortSignal,
) => {
  const search = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  });
  return dataMapRequest(
    HttpUtils.getData<Paging<CrawlTask>>(`${API}/tasks?${search.toString()}`, {
      signal,
      skipErrorHandler: true,
    }),
  );
};

export const getTask = (taskId: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.getData<CrawlTask>(`${API}/tasks/${encodeURIComponent(taskId)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const createTask = (input: CrawlTaskSave) =>
  dataMapRequest(HttpUtils.postData<CrawlTask>(`${API}/tasks`, input, { skipErrorHandler: true }));

export const updateTask = (taskId: string, input: CrawlTaskSave) =>
  dataMapRequest(
    HttpUtils.putData<CrawlTask>(`${API}/tasks/${encodeURIComponent(taskId)}`, input, {
      skipErrorHandler: true,
    }),
  );

export const changeTaskStatus = (taskId: string, status: CrawlTaskStatus) =>
  dataMapRequest(
    HttpUtils.putData<CrawlTask>(
      `${API}/tasks/${encodeURIComponent(taskId)}/status`,
      { status },
      { skipErrorHandler: true },
    ),
  );

export const deleteTask = (taskId: string) =>
  dataMapRequest(
    HttpUtils.deleteData<boolean>(`${API}/tasks/${encodeURIComponent(taskId)}`, undefined, {
      skipErrorHandler: true,
    }),
  );

export const runTask = (taskId: string) =>
  dataMapRequest(
    HttpUtils.postData<Crawl>(
      `${API}/tasks/${encodeURIComponent(taskId)}/run`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export const retryTaskRun = (taskId: string, crawlId: string) =>
  dataMapRequest(
    HttpUtils.postData<Crawl>(
      `${API}/tasks/${encodeURIComponent(taskId)}/runs/${encodeURIComponent(crawlId)}/retry`,
      undefined,
      { skipErrorHandler: true },
    ),
  );

export const previewExclude = (
  input: { datasourceId: string; database: string; schema?: string; regex: string },
  signal?: AbortSignal,
) =>
  dataMapRequest(
    HttpUtils.postData<CrawlExcludePreview>(`${API}/tasks/preview-exclude`, input, {
      signal,
      skipErrorHandler: true,
    }),
  );

export const previewTaskSchedule = (cronExpression: string, signal?: AbortSignal) =>
  dataMapRequest(
    HttpUtils.postData<CrawlSchedulePreview>(
      `${API}/tasks/preview-schedule`,
      { cronExpression },
      { signal, skipErrorHandler: true },
    ),
  );
