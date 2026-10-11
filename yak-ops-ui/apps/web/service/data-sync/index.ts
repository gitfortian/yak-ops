import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";

import {
  createDefinitionRequest,
  definitionPageRequest,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "./definition";

import type {
  DataSyncAttemptRecord,
  DataSyncExecutionEventRecord,
  DataSyncInstancePageParams,
  DataSyncInstancePageResult,
  DataSyncInstanceRecord,
  DataSyncOperationsDashboard,
  DataSyncOperationsDashboardPayload,
  DataSyncSchedulePreview,
  DataSyncScheduleRecord,
  DataSyncScheduleSavePayload,
  DataSyncSinkTraceRecord,
  DataSyncSourceTraceRecord,
  DataSyncTaskOperationPageResult,
  DataSyncTaskPageParams,
  DataSyncTaskPageResult,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
  DataSyncTaskUpdatePayload,
  DataSyncTracePage,
  DataSyncTraceQuery,
  DataSyncTraceSummary,
  TaskDefinitionPageResult,
  TaskDefinitionRecord,
} from "./types";

export type * from "./types";

const DATA_SYNC_API_PREFIX = "/api/v1/data-sync";
const TASK_DEFINITION_API_PREFIX = "/api/v1/task-definitions";

export const listDataSyncTasks = async (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskPageResult> => {
  const page = await HttpUtils.postData<TaskDefinitionPageResult>(
    `${TASK_DEFINITION_API_PREFIX}/page`,
    definitionPageRequest(params),
  );
  return { ...page, bizData: page.bizData.map(toSyncTaskRecord) };
};

export const listDataSyncOperationTasks = (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskOperationPageResult> =>
  HttpUtils.postData<DataSyncTaskOperationPageResult>(
    `${DATA_SYNC_API_PREFIX}/operations/tasks/page`,
    params,
  );

export const getDataSyncOperationsDashboard = (
  payload: DataSyncOperationsDashboardPayload,
): Promise<DataSyncOperationsDashboard> =>
  HttpUtils.postData<DataSyncOperationsDashboard>(
    `${DATA_SYNC_API_PREFIX}/operations/dashboard`,
    payload,
  );

export const getDataSyncTask = async (id: string): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.getData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
  );
  return toSyncTaskRecord(definition);
};

export const createDataSyncTask = async (
  payload: DataSyncTaskSavePayload,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.postData<TaskDefinitionRecord>(
    TASK_DEFINITION_API_PREFIX,
    createDefinitionRequest(payload),
  );
  return toSyncTaskRecord(definition);
};

export const updateDataSyncTask = async (
  id: string,
  payload: DataSyncTaskUpdatePayload,
): Promise<DataSyncTaskRecord> => {
  const definition = await HttpUtils.putData<TaskDefinitionRecord>(
    `${TASK_DEFINITION_API_PREFIX}/${id}`,
    updateDefinitionRequest(payload),
  );
  return toSyncTaskRecord(definition);
};

export const deleteDataSyncTask = async (id: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${TASK_DEFINITION_API_PREFIX}/${id}`);
};

export const publishDataSyncTask = (id: string): Promise<DataSyncTaskRecord> =>
  HttpUtils.postData<DataSyncTaskRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}/publish`);

export const unpublishDataSyncTask = (id: string): Promise<DataSyncTaskRecord> =>
  HttpUtils.postData<DataSyncTaskRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}/unpublish`);

export const runDataSyncTask = (id: string): Promise<DataSyncInstanceRecord> =>
  HttpUtils.postData<DataSyncInstanceRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}/run`);

export const listDataSyncInstances = (
  params: DataSyncInstancePageParams,
): Promise<DataSyncInstancePageResult> =>
  HttpUtils.postData<DataSyncInstancePageResult>(`${DATA_SYNC_API_PREFIX}/instances/page`, params);

export const getDataSyncInstance = (id: string): Promise<DataSyncInstanceRecord> =>
  HttpUtils.getData<DataSyncInstanceRecord>(`${DATA_SYNC_API_PREFIX}/instances/${id}`);

export const cancelDataSyncInstance = (id: string): Promise<DataSyncInstanceRecord> =>
  HttpUtils.postData<DataSyncInstanceRecord>(`${DATA_SYNC_API_PREFIX}/instances/${id}/cancel`);

export const listDataSyncAttempts = (id: string): Promise<DataSyncAttemptRecord[]> =>
  HttpUtils.getData<DataSyncAttemptRecord[]>(`${DATA_SYNC_API_PREFIX}/instances/${id}/attempts`);

export const listDataSyncExecutionEvents = (id: string): Promise<DataSyncExecutionEventRecord[]> =>
  HttpUtils.getData<DataSyncExecutionEventRecord[]>(`${DATA_SYNC_API_PREFIX}/instances/${id}/logs`);

const traceQuery = (query?: DataSyncTraceQuery) => {
  const params = new URLSearchParams();
  if (query?.attemptNo) params.set("attemptNo", String(query.attemptNo));
  if (query?.pageSize) params.set("pageSize", String(query.pageSize));
  if (query?.cursor) params.set("cursor", query.cursor);
  if (query?.status) params.set("status", query.status);
  const value = params.toString();
  return value ? `?${value}` : "";
};

export const getDataSyncTraceSummary = (
  id: string,
  attemptNo?: number,
): Promise<DataSyncTraceSummary> =>
  HttpUtils.getData<DataSyncTraceSummary>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/summary${traceQuery({ attemptNo })}`,
  );

export const listDataSyncSourceTrace = (
  id: string,
  query?: DataSyncTraceQuery,
): Promise<DataSyncTracePage<DataSyncSourceTraceRecord>> =>
  HttpUtils.getData<DataSyncTracePage<DataSyncSourceTraceRecord>>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/source${traceQuery(query)}`,
  );

export const listDataSyncSinkTrace = (
  id: string,
  query?: DataSyncTraceQuery,
): Promise<DataSyncTracePage<DataSyncSinkTraceRecord>> =>
  HttpUtils.getData<DataSyncTracePage<DataSyncSinkTraceRecord>>(
    `${DATA_SYNC_API_PREFIX}/instances/${id}/trace/sink${traceQuery(query)}`,
  );

const DATA_SYNC_SCHEDULE_NOT_FOUND_CODE = 42015;

export const getDataSyncSchedule = async (
  id: string,
): Promise<DataSyncScheduleRecord | undefined> => {
  try {
    const schedule = await HttpUtils.getData<DataSyncScheduleRecord | null>(
      `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule`,
      { skipErrorHandler: true },
    );
    return schedule || undefined;
  } catch (error) {
    if (error instanceof BizError && error.code === DATA_SYNC_SCHEDULE_NOT_FOUND_CODE) {
      return undefined;
    }
    throw error;
  }
};

export const saveDataSyncSchedule = (
  id: string,
  payload: DataSyncScheduleSavePayload,
): Promise<DataSyncScheduleRecord> =>
  HttpUtils.putData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule`,
    payload,
  );

export const previewDataSyncSchedule = (
  payload: DataSyncScheduleSavePayload,
): Promise<DataSyncSchedulePreview> =>
  HttpUtils.postData<DataSyncSchedulePreview>(
    `${DATA_SYNC_API_PREFIX}/schedules/preview`,
    payload,
    { skipErrorHandler: true },
  );

export const enableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/enable`);

export const disableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/disable`,
  );
