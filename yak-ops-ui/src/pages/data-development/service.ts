import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { YakEditorSettings } from './editors/sql/editorSettings';
import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
  DevelopmentTaskRunResult,
  SaveDevelopmentTaskDraftPayload,
} from './types';

const DATA_DEVELOPMENT_API = '/api/v1/data-development';
const DIRECTORY_API = `${DATA_DEVELOPMENT_API}/directories`;
const NODE_API = `${DATA_DEVELOPMENT_API}/nodes`;
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

export const listDevelopmentNodes = (): Promise<ApiResponse<DevelopmentNode[]>> =>
  HttpUtils.get<DevelopmentNode[]>(NODE_API);

export const createDevelopmentNode = (
  payload: CreateDevelopmentNodePayload,
): Promise<ApiResponse<DevelopmentNode>> =>
  HttpUtils.post<DevelopmentNode>(NODE_API, payload);

export const renameDevelopmentNode = (
  id: DevelopmentId,
  name: string,
): Promise<ApiResponse<DevelopmentNode>> =>
  HttpUtils.put<DevelopmentNode>(`${NODE_API}/${id}/name`, { name });

export const deleteDevelopmentNode = (
  id: DevelopmentId,
): Promise<ApiResponse<boolean>> => HttpUtils.delete<boolean>(`${NODE_API}/${id}`);

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
