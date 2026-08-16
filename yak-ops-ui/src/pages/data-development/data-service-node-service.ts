import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

const NODE_API = '/api/v1/data-development/nodes';

export type DataServiceContractType =
  | 'STRING'
  | 'INTEGER'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'OBJECT';

export interface DevelopmentDataServiceParameter {
  name: string;
  type: DataServiceContractType;
  required: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DevelopmentDataServiceResponseField {
  name: string;
  type: DataServiceContractType;
  nullable: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DevelopmentDataServiceDefinition {
  sourceTaskAssetId: DevelopmentId;
  sourceTaskRevisionId: DevelopmentId;
  sourceTaskRevisionNo: number;
  serviceName: string;
  path: string;
  method: 'GET';
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
  maxRows: number;
  timeoutSeconds: number;
  description?: string | null;
}

export interface DevelopmentDataServiceDraft {
  nodeId: DevelopmentId;
  definition: DevelopmentDataServiceDefinition;
  draftRevision: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface DevelopmentDataServiceSource {
  nodeId?: DevelopmentId | null;
  nodeName: string;
  taskAssetId: DevelopmentId;
  status: string;
  revisionId: DevelopmentId;
  revisionNo: number;
  currentRevisionId?: DevelopmentId | null;
  currentRevisionNo?: number | null;
  updateAvailable: boolean;
}

export interface DevelopmentDataServiceRevisionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  sourceTaskRevisionId: DevelopmentId;
  sourceTaskRevisionNo: number;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentDataServiceRevision {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  definition: DevelopmentDataServiceDefinition;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentDataServiceNodeContext {
  nodeId: DevelopmentId;
  nodeName: string;
  configured: boolean;
  availableSources: DevelopmentDataServiceSource[];
  selectedSource?: DevelopmentDataServiceSource | null;
  draft: DevelopmentDataServiceDraft;
  latestPublishedRevision?: DevelopmentDataServiceRevisionSummary | null;
  revisions: DevelopmentDataServiceRevisionSummary[];
}

export interface PreviewDevelopmentDataServiceResult {
  source: DevelopmentDataServiceSource;
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
}

export interface SaveDevelopmentDataServiceDraftPayload {
  sourceTaskAssetId: DevelopmentId;
  sourceTaskRevisionId: DevelopmentId;
  serviceName: string;
  path: string;
  method: 'GET';
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
  maxRows: number;
  timeoutSeconds: number;
  description?: string;
  baseRevision: number;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const getDevelopmentDataServiceNode = async (
  nodeId: DevelopmentId,
): Promise<DevelopmentDataServiceNodeContext> => unwrap(
  await HttpUtils.get<DevelopmentDataServiceNodeContext>(`${NODE_API}/${nodeId}/data-service`),
  '查询 Data Service Node 失败',
);

export const previewDevelopmentDataServiceNode = async (
  nodeId: DevelopmentId,
  sourceTaskAssetId: DevelopmentId,
  sourceTaskRevisionId: DevelopmentId,
): Promise<PreviewDevelopmentDataServiceResult> => unwrap(
  await HttpUtils.post<PreviewDevelopmentDataServiceResult>(
    `${NODE_API}/${nodeId}/data-service/preview`,
    { sourceTaskAssetId, sourceTaskRevisionId },
  ),
  '预览 Data Service Contract 失败',
);

export const saveDevelopmentDataServiceDraft = async (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentDataServiceDraftPayload,
): Promise<DevelopmentDataServiceNodeContext> => unwrap(
  await HttpUtils.put<DevelopmentDataServiceNodeContext>(
    `${NODE_API}/${nodeId}/data-service/draft`,
    payload,
  ),
  '保存 Data Service Node 草稿失败',
);

export const publishDevelopmentDataServiceNode = async (
  nodeId: DevelopmentId,
  expectedDraftRevision: number,
): Promise<DevelopmentDataServiceRevision> => unwrap(
  await HttpUtils.post<DevelopmentDataServiceRevision>(
    `${NODE_API}/${nodeId}/data-service/publish`,
    { expectedDraftRevision },
  ),
  '发布 Data Service Node 失败',
);

export const listDevelopmentDataServiceRevisions = async (
  nodeId: DevelopmentId,
): Promise<DevelopmentDataServiceRevisionSummary[]> => unwrap(
  await HttpUtils.get<DevelopmentDataServiceRevisionSummary[]>(
    `${NODE_API}/${nodeId}/data-service/revisions`,
  ),
  '查询 Data Service Node 版本失败',
);
