import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId, DevelopmentSqlResultColumn } from './types';

const NODE_API = '/api/v1/data-development/nodes';

export type DevelopmentDatasetStatus = 'ONLINE' | 'OFFLINE';
export type DevelopmentDatasetFieldRole = 'DIMENSION' | 'MEASURE';
export type DevelopmentDatasetFieldType =
  | 'STRING'
  | 'NUMBER'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'UNKNOWN';

export interface DevelopmentDatasetFieldDraft {
  fieldId?: string | null;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
}

export interface DevelopmentDatasetNodeVersion {
  versionId: DevelopmentId;
  versionNo: number;
  sourceType: string;
  sourceTaskAssetId?: DevelopmentId;
  sourceTaskRevisionId?: DevelopmentId;
  sourceTaskRevisionNo?: number;
  dataSourceId?: DevelopmentId | null;
  sql?: string | null;
  createTime?: string;
}

export interface DevelopmentDatasetNodeField {
  fieldId: string;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
  sortOrder: number;
}

export interface DevelopmentDatasetNodeAsset {
  developmentNodeId: DevelopmentId;
  datasetId: DevelopmentId;
  name: string;
  description?: string | null;
  status: DevelopmentDatasetStatus;
  currentVersion?: DevelopmentDatasetNodeVersion | null;
  versions: DevelopmentDatasetNodeVersion[];
  fields: DevelopmentDatasetNodeField[];
  createTime?: string;
  updateTime?: string;
}

export interface DevelopmentDatasetNodeContext {
  nodeId: DevelopmentId;
  nodeName: string;
  configured: boolean;
  dataset?: DevelopmentDatasetNodeAsset | null;
}

export interface DevelopmentDatasetQueryResult {
  fields: DevelopmentDatasetFieldDraft[];
  columns: DevelopmentSqlResultColumn[];
  rows: unknown[][];
  returnedRows: number;
  truncated: boolean;
  durationMs: number;
}

export interface SaveDevelopmentDatasetNodePayload {
  dataSourceId: DevelopmentId;
  sql: string;
  description?: string;
  fields?: DevelopmentDatasetFieldDraft[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const getDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
): Promise<DevelopmentDatasetNodeContext> => unwrap(
  await HttpUtils.get<DevelopmentDatasetNodeContext>(`${NODE_API}/${nodeId}/dataset`),
  '查询 Dataset Node 失败',
);

export const previewDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
  dataSourceId: DevelopmentId,
  sql: string,
): Promise<DevelopmentDatasetFieldDraft[]> => unwrap(
  await HttpUtils.post<DevelopmentDatasetFieldDraft[]>(
    `${NODE_API}/${nodeId}/dataset/preview`,
    { dataSourceId, sql },
  ),
  '发现 Dataset Node 字段失败',
);

export const runDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
  dataSourceId: DevelopmentId,
  sql: string,
): Promise<DevelopmentDatasetQueryResult> => unwrap(
  await HttpUtils.post<DevelopmentDatasetQueryResult>(
    `${NODE_API}/${nodeId}/dataset/query`,
    { dataSourceId, sql },
  ),
  '运行 Dataset Node 查询失败',
);

export const saveDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentDatasetNodePayload,
): Promise<DevelopmentDatasetNodeContext> => unwrap(
  await HttpUtils.put<DevelopmentDatasetNodeContext>(`${NODE_API}/${nodeId}/dataset`, payload),
  '保存 Dataset Node 失败',
);
