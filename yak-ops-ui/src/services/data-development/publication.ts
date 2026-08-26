import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

const DATA_SERVICE_API = '/api/v1/data-service';
export const DATA_SERVICE_NODE_SOURCE =
  'DATA_DEVELOPMENT_DATA_SERVICE' as const;

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

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : '';
};

export const fetchDataServicePublicationState = (
  nodeId: DevelopmentId,
): Promise<DataServicePublicationState> =>
  HttpUtils.getData<DataServicePublicationState>(
    `${DATA_SERVICE_API}/publication/state${queryString({
      sourceType: DATA_SERVICE_NODE_SOURCE,
      sourceRef: nodeId,
    })}`,
  );

/** Product-level online action hiding publish, republish and enable details. */
export const bringDataServiceOnline = async (
  nodeId: DevelopmentId,
  state?: DataServicePublicationState,
): Promise<DataServiceRuntimeApiSnapshot> => {
  if (!state?.published) {
    return HttpUtils.postData<DataServiceRuntimeApiSnapshot>(
      `${DATA_SERVICE_API}/publish`,
      {
        sourceType: DATA_SERVICE_NODE_SOURCE,
        sourceRef: nodeId,
        enabled: true,
      },
    );
  }

  const apiId = state.detail?.id;
  if (!apiId || !state.detail) {
    throw new Error('线上 API 身份缺失，请刷新服务状态后重试');
  }

  let detail = state.detail;
  if (state.updateAvailable) {
    detail = await HttpUtils.postData<DataServiceRuntimeApiSnapshot>(
      `${DATA_SERVICE_API}/${encodeURIComponent(apiId)}/republish`,
      {},
    );
  }

  if (!detail.enabled) {
    detail = await HttpUtils.putData<DataServiceRuntimeApiSnapshot>(
      `${DATA_SERVICE_API}/${encodeURIComponent(apiId)}/enabled${queryString({
        enabled: true,
      })}`,
      {},
    );
  }

  return detail;
};
