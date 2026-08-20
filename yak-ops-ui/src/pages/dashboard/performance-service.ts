import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

const PERFORMANCE_API = '/api/v1/datasets/query-performance';

export interface DashboardQueryPerformance {
  queryId: string;
  datasetId: string;
  datasetName: string;
  datasetVersionId: string;
  datasetVersionNo: number;
  sourceType: 'QUERY_REVISION' | 'SQL_QUERY' | 'TABLE' | 'VIEW';
  dataSourceId?: string | null;
  sql: string;
  waitMillis: number;
  prepareMillis: number;
  executeMillis: number;
  transferMillis: number;
  totalMillis: number;
  returnedRows: number;
  truncated: boolean;
  startedAt: string;
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const fetchDashboardQueryPerformance = async (
  queryIds: string[],
): Promise<DashboardQueryPerformance[]> => {
  if (!queryIds.length) return [];
  const params = new URLSearchParams();
  queryIds.forEach((queryId) => params.append('queryIds', queryId));
  params.set('limit', String(Math.min(queryIds.length, 200)));
  return unwrap(
    await HttpUtils.get<DashboardQueryPerformance[]>(`${PERFORMANCE_API}?${params.toString()}`),
    '查询 Dataset 性能分析记录失败',
  );
};
