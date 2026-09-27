import HttpUtils from "@/service/http/HttpUtils";

import type {
  DataSyncMappingPreview,
  DataSyncMappingPreviewPayload,
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
