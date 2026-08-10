import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
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

export const listDevelopmentNodes = (): Promise<ApiResponse<DevelopmentNode[]>> =>
  HttpUtils.get<DevelopmentNode[]>(NODE_API);

export const createDevelopmentNode = (
  payload: CreateDevelopmentNodePayload,
): Promise<ApiResponse<DevelopmentNode>> =>
  HttpUtils.post<DevelopmentNode>(NODE_API, payload);
