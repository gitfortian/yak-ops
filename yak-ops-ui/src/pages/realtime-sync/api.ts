import { request } from '@umijs/max';
import type {
  ApiResponse,
  CdcPipelineSpec,
  DataSourceOption,
  RealtimeDeployment,
  RealtimeEvent,
  RealtimeJob,
  RealtimeJobPage,
  RealtimeObservability,
  RealtimeRuntimeLog,
  ReleaseState,
  RuntimeCapabilities,
} from './types';

const PREFIX = '/api/v1/realtime-sync';

export interface RealtimePageQuery {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  id?: number;
  releaseState?: ReleaseState;
  stateGroup?: 'RUNNING' | 'STOPPED' | 'ABNORMAL';
}

export const realtimeApi = {
  page: (params: RealtimePageQuery) =>
    request<ApiResponse<RealtimeJobPage>>(PREFIX, {
      params,
    }),
  detail: (id: number) => request<ApiResponse<RealtimeJob>>(`${PREFIX}/${id}`),
  createBasic: (payload: { name: string; description?: string }) =>
    request<ApiResponse<number>>(PREFIX, { method: 'POST', data: payload }),
  create: (payload: { name: string; description?: string; spec: CdcPipelineSpec }) =>
    request<ApiResponse<number>>(`${PREFIX}/draft`, { method: 'POST', data: payload }),
  update: (id: number, payload: { name: string; description?: string; spec: CdcPipelineSpec }) =>
    request<ApiResponse<number>>(`${PREFIX}/${id}`, { method: 'PUT', data: payload }),
  action: (id: number, action: 'publish' | 'validate' | 'start' | 'stop' | 'restart' | 'reconcile') =>
    request<ApiResponse<RealtimeDeployment | boolean>>(`${PREFIX}/${id}/${action}`, {
      method: 'POST',
      headers:
        action === 'start' || action === 'restart'
          ? { 'Idempotency-Key': crypto.randomUUID() }
          : undefined,
    }),
  remove: (id: number) => request<ApiResponse<boolean>>(`${PREFIX}/${id}`, { method: 'DELETE' }),
  events: (id: number) => request<ApiResponse<RealtimeEvent[]>>(`${PREFIX}/${id}/events`),
  observability: (id: number) =>
    request<ApiResponse<RealtimeObservability>>(`${PREFIX}/${id}/observability`),
  submissionLog: (id: number, tail = 500) =>
    request<ApiResponse<{ logs: string }>>(`${PREFIX}/${id}/logs/submission`, { params: { tail } }),
  runtimeLog: (id: number, maxExceptions = 50) =>
    request<ApiResponse<RealtimeRuntimeLog>>(`${PREFIX}/${id}/logs/runtime`, {
      params: { maxExceptions },
    }),
  // Compatibility calls retained for older screens/integrations.
  logs: (id: number) =>
    request<ApiResponse<{ logs: string }>>(`${PREFIX}/${id}/logs`, { params: { tail: 500 } }),
  checkpoints: (id: number) => request<ApiResponse<unknown>>(`${PREFIX}/${id}/checkpoints`),
  metrics: (id: number) => request<ApiResponse<unknown>>(`${PREFIX}/${id}/metrics`),
  capabilities: () => request<ApiResponse<RuntimeCapabilities>>(`${PREFIX}/runtime/capabilities`),
  dataSources: () => request<ApiResponse<DataSourceOption[]>>('/api/v1/data-source/option'),
};
