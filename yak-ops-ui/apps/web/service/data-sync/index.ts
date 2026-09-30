import HttpUtils from "@/service/http/HttpUtils";
import { BizError } from "@/service/http/request";

import type {
  DataSyncAttemptRecord,
  DataSyncInstancePageParams,
  DataSyncInstancePageResult,
  DataSyncInstanceRecord,
  DataSyncMappingPreview,
  DataSyncMappingPreviewPayload,
  DataSyncScheduleRecord,
  DataSyncScheduleSavePayload,
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


const DATA_SYNC_SCHEDULE_NOT_FOUND_CODE = 42015;

export const getDataSyncSchedule = async (
  id: string,
): Promise<DataSyncScheduleRecord | undefined> => {
  try {
    return await HttpUtils.getData<DataSyncScheduleRecord>(
      `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule`,
      { skipErrorHandler: true },
    );
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

export const enableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/enable`,
  );

export const disableDataSyncSchedule = (id: string): Promise<DataSyncScheduleRecord> =>
  HttpUtils.postData<DataSyncScheduleRecord>(
    `${DATA_SYNC_API_PREFIX}/tasks/${id}/schedule/disable`,
  );
