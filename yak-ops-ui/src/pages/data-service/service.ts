import HttpUtils from '@/utils/HttpUtils';

export interface CommonApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

export interface DataSourceOption {
  label: string;
  value: string;
  dbType?: string;
}

export interface DataServiceApi {
  id: number;
  name: string;
  path: string;
  runtimePath: string;
  dataSourceId: number;
  sql: string;
  parameterNames: string[];
  maxRows: number;
  timeoutSeconds: number;
  enabled: boolean;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataServiceSavePayload {
  name: string;
  path: string;
  dataSourceId: number;
  sql: string;
  maxRows?: number;
  timeoutSeconds?: number;
  enabled?: boolean;
  description?: string;
}

export interface DataServiceQueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  truncated: boolean;
  rowCount: number;
  durationMs: number;
}

export interface DataServiceCallLog {
  id: number;
  apiId: number;
  serviceName: string;
  servicePath: string;
  paramsJson?: string;
  success: boolean;
  durationMs: number;
  rowCount: number;
  errorMessage?: string;
  createTime?: string;
}

const PREFIX = '/api/v1/data-service';

export const fetchDataServices = () =>
  HttpUtils.get<DataServiceApi[]>(PREFIX) as Promise<CommonApiResponse<DataServiceApi[]>>;

export const createDataService = (payload: DataServiceSavePayload) =>
  HttpUtils.post<DataServiceApi>(PREFIX, payload) as Promise<CommonApiResponse<DataServiceApi>>;

export const updateDataService = (id: number, payload: DataServiceSavePayload) =>
  HttpUtils.put<DataServiceApi>(`${PREFIX}/${id}`, payload) as Promise<CommonApiResponse<DataServiceApi>>;

export const deleteDataService = (id: number) =>
  HttpUtils.delete<boolean>(`${PREFIX}/${id}`) as Promise<CommonApiResponse<boolean>>;

export const setDataServiceEnabled = (id: number, enabled: boolean) =>
  HttpUtils.put<DataServiceApi>(`${PREFIX}/${id}/enabled?enabled=${enabled}`, {}) as Promise<CommonApiResponse<DataServiceApi>>;

export const testDataService = (id: number, parameters: Record<string, string>) =>
  HttpUtils.post<DataServiceQueryResult>(`${PREFIX}/${id}/test`, parameters) as Promise<CommonApiResponse<DataServiceQueryResult>>;

export const fetchDataServiceLogs = () =>
  HttpUtils.get<DataServiceCallLog[]>(`${PREFIX}/logs/recent`) as Promise<CommonApiResponse<DataServiceCallLog[]>>;

export const fetchDataSourceOptions = () =>
  HttpUtils.get<DataSourceOption[]>('/api/v1/data-source/option') as Promise<CommonApiResponse<DataSourceOption[]>>;
