import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

export type HomeDataCenterPeriod = 'yesterday' | '7d' | '30d';
export type HomeTaskType = 'OFFLINE_SYNC' | 'WORKFLOW' | 'DATA_QUALITY';

export interface HomeDataCenterPeriodView {
  start: string;
  end: string;
}

export interface HomeLatestTask {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  durationMs: number;
  runCount: number;
  exceptionCount: number;
  status: string;
  detailPath: string;
}

export interface HomeDataCenterMetrics {
  successCount: number;
  runningCount: number;
  failedCount: number;
  scheduleCount: number;
  processedRecords: number;
  avgDurationMs: number;
}

export interface HomeDataCenterMetricCompare {
  successCount: number;
  runningCount: number;
  failedCount: number;
  scheduleCount: number;
  processedRecordsRate: number;
  avgDurationMs: number;
}

export interface HomeDataCenterOverview {
  period: HomeDataCenterPeriodView;
  latestTask?: HomeLatestTask;
  trend: {
    labels: string[];
    values: number[];
  };
  metrics: HomeDataCenterMetrics;
  compare: HomeDataCenterMetricCompare;
}

export interface HomeRecentTask {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  lastRunTime: string;
  runCount: number;
  successCount: number;
  failedCount: number;
  lastDurationMs: number;
  lastStatus: string;
  detailPath: string;
}

export interface HomeRecentResponse {
  items: HomeRecentTask[];
}

export interface HomeScheduleItem {
  taskId: string;
  taskType: HomeTaskType | string;
  taskName: string;
  cronExpression?: string;
  status: string;
  lastScheduleTime?: string;
  nextScheduleTime?: string;
  detailPath: string;
}

export interface HomeScheduleResponse {
  period: HomeDataCenterPeriodView;
  total: number;
  items: HomeScheduleItem[];
}

const PREFIX = '/api/v1/home/data-center';

export const homeDataCenterApi = {
  overview: (
    period: HomeDataCenterPeriod,
  ): Promise<ApiResponse<HomeDataCenterOverview>> =>
    HttpUtils.get<HomeDataCenterOverview>(
      `${PREFIX}/overview?period=${encodeURIComponent(period)}`,
    ),
  recent: (): Promise<ApiResponse<HomeRecentResponse>> =>
    HttpUtils.get<HomeRecentResponse>(`${PREFIX}/recent`),
  schedule: (
    period: HomeDataCenterPeriod,
  ): Promise<ApiResponse<HomeScheduleResponse>> =>
    HttpUtils.get<HomeScheduleResponse>(
      `${PREFIX}/schedule?period=${encodeURIComponent(period)}`,
    ),
};
