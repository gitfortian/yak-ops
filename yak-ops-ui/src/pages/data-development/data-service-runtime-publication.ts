import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

const DATA_SERVICE_API = '/api/v1/data-service';
export const DATA_SERVICE_NODE_SOURCE = 'DATA_DEVELOPMENT_DATA_SERVICE' as const;

export interface DataServicePublicationSource {
  sourceType: string;
  sourceRef: string;
  name: string;
  sourceKind: string;
  status: string;
  sourceRevisionId: number | string;
  sourceRevisionNo: number;
  dataSourceId: number | string;
  maxRows?: number | null;
  timeoutSeconds?: number | null;
  defaultPath: string;
  description?: string | null;
  updateTime?: string | null;
}

export interface DataServiceRuntimeApiSnapshot {
  id: number | string;
  name: string;
  path: string;
  runtimePath: string;
  enabled: boolean;
  sourceType?: string | null;
  sourceRef?: string | null;
  sourceRevisionId?: number | string | null;
  sourceRevisionNo?: number | null;
}

export interface DataServicePublicationState {
  published: boolean;
  updateAvailable: boolean;
  source: DataServicePublicationSource;
  detail?: DataServiceRuntimeApiSnapshot | null;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const fetchDataServicePublicationState = async (
  nodeId: DevelopmentId,
): Promise<DataServicePublicationState> => unwrap(
  await HttpUtils.get<DataServicePublicationState>(
    `${DATA_SERVICE_API}/publication/state?sourceType=${encodeURIComponent(DATA_SERVICE_NODE_SOURCE)}&sourceRef=${encodeURIComponent(nodeId)}`,
  ),
  '查询 Data Service Runtime 同步状态失败',
);

export const deployDataServiceRuntime = async (
  nodeId: DevelopmentId,
): Promise<DataServiceRuntimeApiSnapshot> => unwrap(
  await HttpUtils.post<DataServiceRuntimeApiSnapshot>(`${DATA_SERVICE_API}/publish`, {
    sourceType: DATA_SERVICE_NODE_SOURCE,
    sourceRef: nodeId,
    enabled: false,
  }),
  '部署 Data Service Runtime 失败',
);

export const syncDataServiceRuntime = async (
  apiId: number | string,
): Promise<DataServiceRuntimeApiSnapshot> => unwrap(
  await HttpUtils.post<DataServiceRuntimeApiSnapshot>(
    `${DATA_SERVICE_API}/${apiId}/republish`,
    {},
  ),
  '同步 Data Service Runtime 失败',
);
