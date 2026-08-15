import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { DataServiceApi } from '../data-service/service';
import { listDevelopmentReleases } from './service';
import type {
  DevelopmentId,
  DevelopmentReleaseSummary,
} from './types';

const RELEASE_API = '/api/v1/data-development/releases';

export interface DevelopmentReleaseDataServiceState {
  published: boolean;
  updateAvailable: boolean;
  releaseRevisionNo: number;
  releaseStatus: 'ONLINE' | 'OFFLINE' | 'DISABLED';
  detail?: DataServiceApi | null;
}

export interface DevelopmentDataServiceContext {
  release?: DevelopmentReleaseSummary;
  dataServiceState?: DevelopmentReleaseDataServiceState;
}

export interface PublishDevelopmentDataServicePayload {
  name?: string;
  path?: string;
  maxRows?: number;
  timeoutSeconds?: number;
  enabled?: boolean;
  description?: string;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const getDevelopmentReleaseDataService = async (
  assetId: DevelopmentId,
): Promise<DevelopmentReleaseDataServiceState> => unwrap(
  await HttpUtils.get<DevelopmentReleaseDataServiceState>(
    `${RELEASE_API}/${assetId}/data-service`,
  ),
  '查询数据服务状态失败',
);

export const publishDevelopmentReleaseDataService = async (
  assetId: DevelopmentId,
  payload: PublishDevelopmentDataServicePayload,
): Promise<DataServiceApi> => unwrap(
  await HttpUtils.post<DataServiceApi>(
    `${RELEASE_API}/${assetId}/data-service`,
    payload,
  ),
  '发布数据服务失败',
);

export const fetchDevelopmentDataServiceContext = async (
  nodeId: DevelopmentId,
  nodeName: string,
): Promise<DevelopmentDataServiceContext> => {
  const page = unwrap(
    await listDevelopmentReleases({
      pageNo: 1,
      pageSize: 100,
      keyword: nodeName,
      taskType: 'SQL',
    }),
    '查询 SQL 发布状态失败',
  );
  const release = page.records.find((item) => item.nodeId === nodeId);
  if (!release) return {};
  return {
    release,
    dataServiceState: await getDevelopmentReleaseDataService(release.assetId),
  };
};
