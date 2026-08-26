import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type { DashboardSummary } from './model';

interface DashboardAssetWire {
  id: string;
  name: string;
  description?: string | null;
  currentVersionId?: string | null;
  currentVersionNo: number;
  publishedVersionId?: string | null;
  publishedVersionNo?: number;
  publishedTime?: string | null;
  createTime?: string;
  updateTime?: string;
}

interface DashboardOverviewWire {
  dashboardCount: number;
  publishedDashboardCount: number;
  recentDashboards?: DashboardAssetWire[];
}

export interface DashboardOverview {
  dashboardCount: number;
  publishedDashboardCount: number;
  recentDashboards: DashboardSummary[];
}

const summary = (value: DashboardAssetWire): DashboardSummary => ({
  id: String(value.id),
  name: value.name,
  description: value.description || '',
  currentVersionId: value.currentVersionId
    ? String(value.currentVersionId)
    : undefined,
  currentVersionNo: value.currentVersionNo || 0,
  publishedVersionId: value.publishedVersionId
    ? String(value.publishedVersionId)
    : undefined,
  publishedVersionNo: value.publishedVersionNo || 0,
  publishedTime: value.publishedTime || undefined,
  createTime: value.createTime,
  updateTime: value.updateTime,
});

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

export const fetchDashboardOverview = async (
  limit = 4,
): Promise<DashboardOverview> => {
  const size = Math.max(1, Math.min(20, Math.trunc(limit)));
  const value = unwrap(
    await HttpUtils.get<DashboardOverviewWire>(
      `/api/v1/dashboards/overview?limit=${size}`,
    ),
    '查询 Dashboard 总览失败',
  );
  return {
    dashboardCount: value.dashboardCount ?? 0,
    publishedDashboardCount: value.publishedDashboardCount ?? 0,
    recentDashboards: (value.recentDashboards || []).map(summary),
  };
};
