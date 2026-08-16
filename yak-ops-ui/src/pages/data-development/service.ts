import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { YakEditorSettings } from './editors/sql/editorSettings';
import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
  DevelopmentGraph,
  DevelopmentId,
  DevelopmentReleaseDetail,
  DevelopmentReleasePage,
  DevelopmentReleaseQuery,
  DevelopmentReleaseSummary,
  DevelopmentResourceNode,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionPage,
  DevelopmentTaskExecutionQuery,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
  DevelopmentTaskRunResult,
  SaveDevelopmentGraphPayload,
  SaveDevelopmentTaskDraftPayload,
} from './types';

const DATA_DEVELOPMENT_API = '/api/v1/data-development';
const DIRECTORY_API = `${DATA_DEVELOPMENT_API}/directories`;
const NODE_API = `${DATA_DEVELOPMENT_API}/nodes`;
const GRAPH_API = `${DATA_DEVELOPMENT_API}/graph`;
const EXECUTION_API = `${DATA_DEVELOPMENT_API}/executions`;
const RELEASE_API = `${DATA_DEVELOPMENT_API}/releases`;
const EDITOR_SETTINGS_API = `${DATA_DEVELOPMENT_API}/editor-settings`;

export const listDevelopmentDirectories = (): Promise<ApiResponse<DevelopmentDirectory[]>> =>
  HttpUtils.get<DevelopmentDirectory[]>(DIRECTORY_API);

export const createDevelopmentDirectory = (
  payload: CreateDevelopmentDirectoryPayload,
): Promise<ApiResponse<DevelopmentDirectory>> =>
  HttpUtils.post<DevelopmentDirectory>(DIRECTORY_API, payload);

export const renameDevelopmentDirectory = (
  id: DevelopmentId,
  name: string,
): Promise<ApiResponse<DevelopmentDirectory>> =>
  HttpUtils.put<DevelopmentDirectory>(`${DIRECTORY_API}/${id}/name`, { name });

export const deleteDevelopmentDirectory = (
  id: DevelopmentId,
): Promise<ApiResponse<boolean>> => HttpUtils.delete<boolean>(`${DIRECTORY_API}/${id}`);

export const listDevelopmentNodes = (): Promise<ApiResponse<DevelopmentResourceNode[]>> =>
  HttpUtils.get<DevelopmentResourceNode[]>(NODE_API);

export const createDevelopmentNode = (
  payload: CreateDevelopmentNodePayload,
): Promise<ApiResponse<DevelopmentResourceNode>> =>
  HttpUtils.post<DevelopmentResourceNode>(NODE_API, payload);

export const renameDevelopmentNode = (
  id: DevelopmentId,
  name: string,
): Promise<ApiResponse<DevelopmentResourceNode>> =>
  HttpUtils.put<DevelopmentResourceNode>(`${NODE_API}/${id}/name`, { name });

export const deleteDevelopmentNode = (
  id: DevelopmentId,
): Promise<ApiResponse<boolean>> => HttpUtils.delete<boolean>(`${NODE_API}/${id}`);

export const getDevelopmentGraph = (
  projectId?: DevelopmentId,
): Promise<ApiResponse<DevelopmentGraph>> =>
  HttpUtils.get<DevelopmentGraph>(
    `${GRAPH_API}${projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''}`,
  );

export const saveDevelopmentGraph = (
  payload: SaveDevelopmentGraphPayload,
): Promise<ApiResponse<DevelopmentGraph>> =>
  HttpUtils.put<DevelopmentGraph>(GRAPH_API, payload);

export const getDevelopmentTaskDraft = (
  nodeId: DevelopmentId,
): Promise<ApiResponse<DevelopmentTaskDraft>> =>
  HttpUtils.get<DevelopmentTaskDraft>(`${NODE_API}/${nodeId}/draft`);

export const saveDevelopmentTaskDraft = (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentTaskDraftPayload,
): Promise<ApiResponse<DevelopmentTaskDraft>> =>
  HttpUtils.put<DevelopmentTaskDraft>(`${NODE_API}/${nodeId}/draft`, payload);

/** Execute the current editor definition; this does not implicitly save or publish it. */
export const runDevelopmentTask = (
  nodeId: DevelopmentId,
  payload: DevelopmentTaskDefinition,
): Promise<ApiResponse<DevelopmentTaskRunResult>> =>
  HttpUtils.post<DevelopmentTaskRunResult>(`${NODE_API}/${nodeId}/run`, payload);

const queryString = (query: object) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : '';
};

export const listDevelopmentTaskExecutions = (
  query: DevelopmentTaskExecutionQuery,
): Promise<ApiResponse<DevelopmentTaskExecutionPage>> =>
  HttpUtils.get<DevelopmentTaskExecutionPage>(
    `${EXECUTION_API}${queryString(query)}`,
  );

export const getDevelopmentTaskExecution = (
  id: DevelopmentId,
): Promise<ApiResponse<DevelopmentTaskExecutionDetail>> =>
  HttpUtils.get<DevelopmentTaskExecutionDetail>(`${EXECUTION_API}/${id}`);

export const listDevelopmentReleases = (
  query: DevelopmentReleaseQuery,
): Promise<ApiResponse<DevelopmentReleasePage>> =>
  HttpUtils.get<DevelopmentReleasePage>(
    `${RELEASE_API}${queryString(query)}`,
  );

export const getDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<ApiResponse<DevelopmentReleaseDetail>> =>
  HttpUtils.get<DevelopmentReleaseDetail>(`${RELEASE_API}/${assetId}`);

export const offlineDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<ApiResponse<DevelopmentReleaseSummary>> =>
  HttpUtils.post<DevelopmentReleaseSummary>(`${RELEASE_API}/${assetId}/offline`);

export const onlineDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<ApiResponse<DevelopmentReleaseSummary>> =>
  HttpUtils.post<DevelopmentReleaseSummary>(`${RELEASE_API}/${assetId}/online`);

export const activateDevelopmentReleaseRevision = (
  assetId: DevelopmentId,
  revisionNo: number,
): Promise<ApiResponse<DevelopmentReleaseSummary>> =>
  HttpUtils.post<DevelopmentReleaseSummary>(
    `${RELEASE_API}/${assetId}/activate/${revisionNo}`,
  );

export const publishDevelopmentTask = (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<ApiResponse<DevelopmentTaskRevision>> =>
  HttpUtils.post<DevelopmentTaskRevision>(`${NODE_API}/${nodeId}/publish`, {
    draftRevision,
  });

export const listDevelopmentTaskRevisions = (
  nodeId: DevelopmentId,
): Promise<ApiResponse<DevelopmentTaskRevisionSummary[]>> =>
  HttpUtils.get<DevelopmentTaskRevisionSummary[]>(`${NODE_API}/${nodeId}/revisions`);

export const getDevelopmentTaskRevision = (
  nodeId: DevelopmentId,
  revisionNo: number,
): Promise<ApiResponse<DevelopmentTaskRevision>> =>
  HttpUtils.get<DevelopmentTaskRevision>(
    `${NODE_API}/${nodeId}/revisions/${revisionNo}`,
  );

export const getDevelopmentEditorSettings = (): Promise<ApiResponse<YakEditorSettings>> =>
  HttpUtils.get<YakEditorSettings>(EDITOR_SETTINGS_API);

export const saveDevelopmentEditorSettings = (
  settings: YakEditorSettings,
): Promise<ApiResponse<YakEditorSettings>> =>
  HttpUtils.put<YakEditorSettings>(EDITOR_SETTINGS_API, settings);
