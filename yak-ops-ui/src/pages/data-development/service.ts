import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
} from './types';

const DATA_DEVELOPMENT_API = '/api/v1/data-development';
const DIRECTORY_API = `${DATA_DEVELOPMENT_API}/directories`;
const NODE_API = `${DATA_DEVELOPMENT_API}/nodes`;

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
