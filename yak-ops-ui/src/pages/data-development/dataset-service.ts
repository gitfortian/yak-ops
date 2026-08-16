import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import { listDevelopmentReleases } from './service';
import type {
  DevelopmentId,
  DevelopmentReleaseSummary,
} from './types';

const RELEASE_API = '/api/v1/data-development/releases';
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

export interface DevelopmentDatasetSummary {
  id: string;
  name: string;
  description?: string | null;
  status: DevelopmentDatasetStatus;
  currentVersionId?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface DevelopmentDatasetVersion {
  id: string;
  datasetId: string;
  versionNo: number;
  sourceType: 'QUERY_REVISION' | 'TABLE' | 'VIEW';
  sourceTaskAssetId: string;
  sourceTaskRevisionId: string;
  sourceTaskRevisionNo: number;
  schemaSnapshot?: string | null;
  createTime?: string;
}

export interface DevelopmentDatasetField {
  fieldId: string;
  versionId: string;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
  sortOrder: number;
}

export interface DevelopmentDatasetFieldDraft {
  fieldId?: string | null;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
}

export interface DevelopmentDatasetDetail {
  dataset: DevelopmentDatasetSummary;
  currentVersion?: DevelopmentDatasetVersion | null;
  versions: DevelopmentDatasetVersion[];
  fields: DevelopmentDatasetField[];
}

export interface DevelopmentReleaseDatasetState {
  published: boolean;
  detail?: DevelopmentDatasetDetail | null;
}

export interface DevelopmentDatasetContext {
  release?: DevelopmentReleaseSummary;
  datasetState?: DevelopmentReleaseDatasetState;
}

export interface PublishDevelopmentDatasetPayload {
  name?: string;
  description?: string;
  fields?: DevelopmentDatasetFieldDraft[];
}

export interface DevelopmentDatasetNodeSource {
  nodeId: DevelopmentId;
  nodeName: string;
  taskAssetId: DevelopmentId;
  status: string;
  revisionId: DevelopmentId;
  revisionNo: number;
}

export interface DevelopmentDatasetNodeVersion {
  versionId: DevelopmentId;
  versionNo: number;
  sourceType: string;
  sourceTaskAssetId: DevelopmentId;
  sourceTaskRevisionId: DevelopmentId;
  sourceTaskRevisionNo: number;
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
  availableSources: DevelopmentDatasetNodeSource[];
  selectedSource?: DevelopmentDatasetNodeSource | null;
  dataset?: DevelopmentDatasetNodeAsset | null;
}

export interface SaveDevelopmentDatasetNodePayload {
  sourceTaskAssetId: DevelopmentId;
  description?: string;
  fields?: DevelopmentDatasetFieldDraft[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const getDevelopmentReleaseDataset = async (
  assetId: DevelopmentId,
): Promise<DevelopmentReleaseDatasetState> => unwrap(
  await HttpUtils.get<DevelopmentReleaseDatasetState>(`${RELEASE_API}/${assetId}/dataset`),
  '查询 Dataset 状态失败',
);

export const previewDevelopmentReleaseDataset = async (
  assetId: DevelopmentId,
): Promise<DevelopmentDatasetFieldDraft[]> => unwrap(
  await HttpUtils.post<DevelopmentDatasetFieldDraft[]>(`${RELEASE_API}/${assetId}/dataset/preview`, {}),
  '发现 Dataset 字段失败',
);

export const publishDevelopmentReleaseDataset = async (
  assetId: DevelopmentId,
  payload: PublishDevelopmentDatasetPayload,
): Promise<DevelopmentDatasetDetail> => unwrap(
  await HttpUtils.post<DevelopmentDatasetDetail>(`${RELEASE_API}/${assetId}/dataset`, payload),
  '发布 Dataset 失败',
);

export const getDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
): Promise<DevelopmentDatasetNodeContext> => unwrap(
  await HttpUtils.get<DevelopmentDatasetNodeContext>(`${NODE_API}/${nodeId}/dataset`),
  '查询 Dataset Node 失败',
);

export const previewDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
  sourceTaskAssetId: DevelopmentId,
): Promise<DevelopmentDatasetFieldDraft[]> => unwrap(
  await HttpUtils.post<DevelopmentDatasetFieldDraft[]>(
    `${NODE_API}/${nodeId}/dataset/preview`,
    { sourceTaskAssetId },
  ),
  '发现 Dataset Node 字段失败',
);

export const saveDevelopmentDatasetNode = async (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentDatasetNodePayload,
): Promise<DevelopmentDatasetNodeContext> => unwrap(
  await HttpUtils.put<DevelopmentDatasetNodeContext>(`${NODE_API}/${nodeId}/dataset`, payload),
  '保存 Dataset Node 失败',
);

export const fetchDevelopmentDatasetContext = async (
  nodeId: DevelopmentId,
  nodeName: string,
): Promise<DevelopmentDatasetContext> => {
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
    datasetState: await getDevelopmentReleaseDataset(release.assetId),
  };
};
