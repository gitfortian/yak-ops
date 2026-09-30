import HttpUtils from "@/service/http/HttpUtils";

import type {
  DataSyncAttemptRecord,
  DataSyncInstancePageParams,
  DataSyncInstancePageResult,
  DataSyncInstanceRecord,
  DataSyncMappingPreview,
  DataSyncMappingPreviewPayload,
  DataSyncTaskOperationPageResult,
  DataSyncTaskPageParams,
  DataSyncTaskPageResult,
  DataSyncTaskRecord,
  DataSyncTaskSavePayload,
} from "./types";

export type * from "./types";

const DATA_SYNC_API_PREFIX = "/api/v1/data-sync";

export const listDataSyncTasks = (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskPageResult> =>
  HttpUtils.postData<DataSyncTaskPageResult>(`${DATA_SYNC_API_PREFIX}/tasks/page`, params);

export const listDataSyncOperationTasks = (
  params: DataSyncTaskPageParams,
): Promise<DataSyncTaskOperationPageResult> =>
  HttpUtils.postData<DataSyncTaskOperationPageResult>(
    `${DATA_SYNC_API_PREFIX}/operations/tasks/page`,
    params,
  );

export const getDataSyncTask = (id: string): Promise<DataSyncTaskRecord> =>
  HttpUtils.getData<DataSyncTaskRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}`);

export const createDataSyncTask = (payload: DataSyncTaskSavePayload): Promise<DataSyncTaskRecord> =>
  HttpUtils.postData<DataSyncTaskRecord>(`${DATA_SYNC_API_PREFIX}/tasks`, payload);

export const updateDataSyncTask = (
  id: string,
  payload: DataSyncTaskSavePayload,
): Promise<DataSyncTaskRecord> =>
  HttpUtils.putData<DataSyncTaskRecord>(`${DATA_SYNC_API_PREFIX}/tasks/${id}`, payload);

export const deleteDataSyncTask = async (id: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${DATA_SYNC_API_PREFIX}/tasks/${id}`);
};

export const previewDataSyncMapping = (
  payload: DataSyncMappingPreviewPayload,
): Promise<DataSyncMappingPreview> =>
  HttpUtils.postData<DataSyncMappingPreview>(
    `${DATA_SYNC_API_PREFIX}/tasks/mapping-preview`,
    payload,
  );

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
