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

export type DataServiceAuthMode = 'NONE' | 'API_KEY';

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
  authMode: DataServiceAuthMode;
  description?: string;
  sourceType?: string;
  sourceRef?: string;
  sourceRevisionId?: number;
  sourceRevisionNo?: number;
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

export interface DataServiceRuntimeConfig {
  cacheEnabled: boolean;
  cacheTtlSeconds: number;
  cacheMaxEntries: number;
  circuitBreakerEnabled: boolean;
  failureThreshold: number;
  recoverySeconds: number;
}

export interface DataServiceRuntimeStatus extends DataServiceRuntimeConfig {
  apiId: number;
  cacheEntries: number;
  circuitState: 'DISABLED' | 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  circuitOpenUntil?: string | null;
  totalCalls: number;
  successCalls: number;
  failureCalls: number;
  cacheHits: number;
  circuitRejected: number;
  successRate: number;
  cacheHitRate: number;
  averageDurationMs: number;
  p95DurationMs: number;
  lastSuccessAt?: string | null;
  lastFailureAt?: string | null;
}

export interface DataServiceApiKey {
  id: number;
  apiId: number;
  name: string;
  keyPrefix: string;
  enabled: boolean;
  rateLimitPerMinute: number;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface DataServiceApiKeyInput {
  name: string;
  rateLimitPerMinute?: number;
  expiresAt?: string | null;
}

export interface DataServiceApiKeyUpdate extends DataServiceApiKeyInput {
  expiresAtSet: boolean;
}

export interface CreatedDataServiceApiKey {
  key: DataServiceApiKey;
  secret: string;
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
  callerType: 'LEGACY' | 'PUBLIC' | 'API_KEY' | 'CONSOLE';
  apiKeyId?: number | null;
  apiKeyName?: string | null;
  apiKeyPrefix?: string | null;
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

export const fetchDataServiceRuntime = (id: number) =>
  HttpUtils.get<DataServiceRuntimeStatus>(`${PREFIX}/${id}/runtime`) as Promise<CommonApiResponse<DataServiceRuntimeStatus>>;

export const updateDataServiceRuntime = (id: number, payload: DataServiceRuntimeConfig) =>
  HttpUtils.put<DataServiceRuntimeStatus>(`${PREFIX}/${id}/runtime`, payload) as Promise<CommonApiResponse<DataServiceRuntimeStatus>>;

export const setDataServiceAuthMode = (id: number, mode: DataServiceAuthMode) =>
  HttpUtils.put<DataServiceAuthMode>(`${PREFIX}/${id}/auth-mode?mode=${mode}`, {}) as Promise<CommonApiResponse<DataServiceAuthMode>>;

export const fetchDataServiceKeys = (id: number) =>
  HttpUtils.get<DataServiceApiKey[]>(`${PREFIX}/${id}/keys`) as Promise<CommonApiResponse<DataServiceApiKey[]>>;

export const createDataServiceKey = (id: number, payload: DataServiceApiKeyInput) =>
  HttpUtils.post<CreatedDataServiceApiKey>(`${PREFIX}/${id}/keys`, payload) as Promise<CommonApiResponse<CreatedDataServiceApiKey>>;

export const updateDataServiceKey = (
  id: number,
  keyId: number,
  payload: DataServiceApiKeyUpdate,
) => HttpUtils.put<DataServiceApiKey>(`${PREFIX}/${id}/keys/${keyId}`, payload) as Promise<CommonApiResponse<DataServiceApiKey>>;

export const setDataServiceKeyEnabled = (id: number, keyId: number, enabled: boolean) =>
  HttpUtils.put<DataServiceApiKey>(`${PREFIX}/${id}/keys/${keyId}/enabled?enabled=${enabled}`, {}) as Promise<CommonApiResponse<DataServiceApiKey>>;

export const rotateDataServiceKey = (id: number, keyId: number) =>
  HttpUtils.post<CreatedDataServiceApiKey>(`${PREFIX}/${id}/keys/${keyId}/rotate`, {}) as Promise<CommonApiResponse<CreatedDataServiceApiKey>>;

export const deleteDataServiceKey = (id: number, keyId: number) =>
  HttpUtils.delete<boolean>(`${PREFIX}/${id}/keys/${keyId}`) as Promise<CommonApiResponse<boolean>>;

export const testDataService = (id: number, parameters: Record<string, string>) =>
  HttpUtils.post<DataServiceQueryResult>(`${PREFIX}/${id}/test`, parameters) as Promise<CommonApiResponse<DataServiceQueryResult>>;

export const fetchDataServiceLogs = () =>
  HttpUtils.get<DataServiceCallLog[]>(`${PREFIX}/logs/recent`) as Promise<CommonApiResponse<DataServiceCallLog[]>>;

export const fetchDataSourceOptions = () =>
  HttpUtils.get<DataSourceOption[]>('/api/v1/data-source/option') as Promise<CommonApiResponse<DataSourceOption[]>>;
