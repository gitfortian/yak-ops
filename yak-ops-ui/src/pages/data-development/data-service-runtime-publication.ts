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
  '查询 Data Service 服务状态失败',
);

/**
 * Product-level "online" action.
 *
 * Runtime publish / republish / enable stay as implementation details so the
 * Data Development UI only needs to expose 上线 / 更新上线.
 */
export const bringDataServiceOnline = async (
  nodeId: DevelopmentId,
  state?: DataServicePublicationState,
): Promise<DataServiceRuntimeApiSnapshot> => {
  if (!state?.published) {
    return unwrap(
      await HttpUtils.post<DataServiceRuntimeApiSnapshot>(`${DATA_SERVICE_API}/publish`, {
        sourceType: DATA_SERVICE_NODE_SOURCE,
        sourceRef: nodeId,
        enabled: true,
      }),
      '上线 Data Service API 失败',
    );
  }

  const apiId = state.detail?.id;
  if (!apiId) throw new Error('线上 API 身份缺失，请刷新服务状态后重试');

  let detail = state.detail;
  if (state.updateAvailable) {
    detail = unwrap(
      await HttpUtils.post<DataServiceRuntimeApiSnapshot>(
        `${DATA_SERVICE_API}/${apiId}/republish`,
        {},
      ),
      '更新上线失败',
    );
  }

  if (!detail?.enabled) {
    detail = unwrap(
      await HttpUtils.put<DataServiceRuntimeApiSnapshot>(
        `${DATA_SERVICE_API}/${apiId}/enabled?enabled=true`,
        {},
      ),
      '启用 Data Service API 失败',
    );
  }

  return detail;
};
