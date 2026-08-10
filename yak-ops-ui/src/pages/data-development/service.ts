import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
  DevelopmentNode,
  SqlTaskDefinition,
  SqlTaskExecution,
  SqlTaskSavePayload,
  SqlTaskUpdatePayload,
  SqlTaskVersion,
} from './types';

const DATA_DEVELOPMENT_API = '/api/v1/data-development';
const SQL_TASK_API = `${DATA_DEVELOPMENT_API}/sql-tasks`;
const DIRECTORY_API = `${DATA_DEVELOPMENT_API}/directories`;
const NODE_API = `${DATA_DEVELOPMENT_API}/nodes`;

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
};

export const listDevelopmentDirectories = (): Promise<ApiResponse<DevelopmentDirectory[]>> =>
  HttpUtils.get<DevelopmentDirectory[]>(DIRECTORY_API);

export const createDevelopmentDirectory = (
  payload: CreateDevelopmentDirectoryPayload,
): Promise<ApiResponse<DevelopmentDirectory>> =>
  HttpUtils.post<DevelopmentDirectory>(DIRECTORY_API, payload);

export const listDevelopmentNodes = (): Promise<ApiResponse<DevelopmentNode[]>> =>
  HttpUtils.get<DevelopmentNode[]>(NODE_API);

export const createDevelopmentNode = (
  payload: CreateDevelopmentNodePayload,
): Promise<ApiResponse<DevelopmentNode>> =>
  HttpUtils.post<DevelopmentNode>(NODE_API, payload);

export const listSqlTasks = (
  projectId?: number,
): Promise<ApiResponse<SqlTaskDefinition[]>> =>
  HttpUtils.get<SqlTaskDefinition[]>(
    `${SQL_TASK_API}${queryString({ projectId })}`,
  );

export const getSqlTask = (
  id: number,
): Promise<ApiResponse<SqlTaskDefinition>> =>
  HttpUtils.get<SqlTaskDefinition>(`${SQL_TASK_API}/${id}`);

export const createSqlTask = (
  payload: SqlTaskSavePayload,
): Promise<ApiResponse<SqlTaskDefinition>> =>
  HttpUtils.post<SqlTaskDefinition>(SQL_TASK_API, payload);

export const updateSqlTask = (
  id: number,
  payload: SqlTaskUpdatePayload,
): Promise<ApiResponse<SqlTaskDefinition>> =>
  HttpUtils.put<SqlTaskDefinition>(`${SQL_TASK_API}/${id}`, payload);

export const publishSqlTask = (
  id: number,
  draftRevision: number,
): Promise<ApiResponse<SqlTaskVersion>> =>
  HttpUtils.post<SqlTaskVersion>(`${SQL_TASK_API}/${id}/publish`, {
    draftRevision,
  });

export const listSqlTaskVersions = (
  id: number,
): Promise<ApiResponse<SqlTaskVersion[]>> =>
  HttpUtils.get<SqlTaskVersion[]>(`${SQL_TASK_API}/${id}/versions`);

export const runSqlTask = (
  id: number,
  input: Record<string, unknown>,
): Promise<ApiResponse<SqlTaskExecution>> =>
  HttpUtils.post<SqlTaskExecution>(`${SQL_TASK_API}/${id}/run`, { input });

export const getSqlTaskExecution = (
  executionId: number,
): Promise<ApiResponse<SqlTaskExecution>> =>
  HttpUtils.get<SqlTaskExecution>(
    `${SQL_TASK_API}/executions/${executionId}`,
  );

export const cancelSqlTaskExecution = (
  executionId: number,
): Promise<ApiResponse<SqlTaskExecution>> =>
  HttpUtils.post<SqlTaskExecution>(
    `${SQL_TASK_API}/executions/${executionId}/cancel`,
    {},
  );
