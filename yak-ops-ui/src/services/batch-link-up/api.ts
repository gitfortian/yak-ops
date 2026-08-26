import HttpUtils from '@/utils/HttpUtils';

import type {
  BatchLinkUpId,
  OfflineBatchOperationResult,
  OfflineJobDefinitionVO,
  OfflineJobExecutionVO,
  OfflineSyncTaskPageQuery,
  PagingData,
} from './types';

const DEFINITION_API = '/api/v1/job/batch-definition';
const EXECUTION_API = '/api/v1/job/batch-execution';

export const listOfflineSyncTasks = (
  query: OfflineSyncTaskPageQuery,
): Promise<PagingData<OfflineJobDefinitionVO>> =>
  HttpUtils.postData<PagingData<OfflineJobDefinitionVO>>(
    `${DEFINITION_API}/page`,
    query,
  );

export const getOfflineSyncTask = (
  id: BatchLinkUpId,
): Promise<OfflineJobDefinitionVO> =>
  HttpUtils.getData<OfflineJobDefinitionVO>(
    `${DEFINITION_API}/${encodeURIComponent(id)}`,
  );

export const getOfflineSyncEditDetail = (
  id: BatchLinkUpId,
): Promise<Record<string, unknown>> =>
  HttpUtils.getData<Record<string, unknown>>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/edit-detail`,
  );

export const getOfflineSyncUniqueId = (): Promise<BatchLinkUpId> =>
  HttpUtils.getData<BatchLinkUpId>(`${DEFINITION_API}/get-unique-id`);

export const createOfflineSyncDraft = (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId> =>
  HttpUtils.postData<BatchLinkUpId>(`${DEFINITION_API}/draft`, payload);

export const saveOfflineSyncSingleGuide = (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId> =>
  HttpUtils.postData<BatchLinkUpId>(
    `${DEFINITION_API}/guide-single/saveOrUpdate`,
    payload,
  );

export const saveOfflineSyncMultiGuide = (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId> =>
  HttpUtils.postData<BatchLinkUpId>(
    `${DEFINITION_API}/guide-multi/saveOrUpdate`,
    payload,
  );

export const deleteOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}`,
  );
};

export const onlineOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.putData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/online`,
  );
};

export const offlineOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.putData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/offline`,
  );
};

export const executeOfflineSyncTask = (
  definitionId: BatchLinkUpId,
): Promise<OfflineJobExecutionVO> =>
  HttpUtils.postData<OfflineJobExecutionVO>(
    `${EXECUTION_API}/${encodeURIComponent(definitionId)}/execute`,
    {},
  );

export const stopOfflineSyncExecution = (
  instanceId: BatchLinkUpId,
): Promise<OfflineJobExecutionVO> =>
  HttpUtils.postData<OfflineJobExecutionVO>(
    `${EXECUTION_API}/${encodeURIComponent(instanceId)}/cancel`,
    {},
  );

export const batchStartOfflineSyncTasks = (
  definitionIds: BatchLinkUpId[],
): Promise<OfflineBatchOperationResult> =>
  HttpUtils.postData<OfflineBatchOperationResult>(
    `${EXECUTION_API}/batch-execute`,
    { jobDefinitionIds: definitionIds.map(Number) },
  );

export const batchStopOfflineSyncTasks = (
  definitionIds: BatchLinkUpId[],
): Promise<OfflineBatchOperationResult> =>
  HttpUtils.postData<OfflineBatchOperationResult>(
    `${EXECUTION_API}/batch-pause`,
    { jobDefinitionIds: definitionIds.map(Number) },
  );
