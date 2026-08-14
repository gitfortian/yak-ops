import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type { AnalysisSpec } from '@/components/analysis/model';
import type {
  DashboardDocument,
  DashboardServerDetail,
  DashboardSummary,
  DashboardVersionSummary,
  DashboardWidget,
} from './model';

const DASHBOARD_API = '/api/v1/dashboards';

interface DashboardAssetWire {
  id: string;
  name: string;
  description?: string | null;
  currentVersionId?: string | null;
  currentVersionNo: number;
  createTime?: string;
  updateTime?: string;
}

interface DashboardVersionWire {
  id: string;
  dashboardId: string;
  versionNo: number;
  name: string;
  description?: string | null;
  activeDatasetId?: string | null;
  createTime?: string;
}

interface DashboardWidgetWire {
  id: string;
  dashboardVersionId: string;
  widgetKey: string;
  analysisId?: string | null;
  title?: string | null;
  inlineAnalysis?: AnalysisSpec | null;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number | null;
  minH?: number | null;
  sortOrder: number;
}

interface DashboardDetailWire {
  dashboard: DashboardAssetWire;
  currentVersion?: DashboardVersionWire | null;
  versions: DashboardVersionWire[];
  widgets: DashboardWidgetWire[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const summary = (value: DashboardAssetWire): DashboardSummary => ({
  id: String(value.id),
  name: value.name,
  description: value.description || '',
  currentVersionId: value.currentVersionId ? String(value.currentVersionId) : undefined,
  currentVersionNo: value.currentVersionNo || 0,
  createTime: value.createTime,
  updateTime: value.updateTime,
});

const version = (value: DashboardVersionWire): DashboardVersionSummary => ({
  id: String(value.id),
  dashboardId: String(value.dashboardId),
  versionNo: value.versionNo,
  name: value.name,
  description: value.description || '',
  activeDatasetId: value.activeDatasetId ? String(value.activeDatasetId) : undefined,
  createTime: value.createTime,
});

const widget = (value: DashboardWidgetWire): DashboardWidget => ({
  id: value.widgetKey,
  analysisId: value.analysisId ? String(value.analysisId) : undefined,
  title: value.title || undefined,
  inlineAnalysis: value.inlineAnalysis || undefined,
  x: value.x,
  y: value.y,
  w: value.w,
  h: value.h,
  minW: value.minW ?? undefined,
  minH: value.minH ?? undefined,
});

export const toDashboardServerDetail = (value: DashboardDetailWire): DashboardServerDetail => ({
  dashboard: summary(value.dashboard),
  currentVersion: value.currentVersion ? version(value.currentVersion) : undefined,
  versions: (value.versions || []).map(version),
  widgets: (value.widgets || []).map(widget),
});

export const toDashboardDocument = (detail: DashboardServerDetail): DashboardDocument => ({
  version: 1,
  id: detail.dashboard.id,
  name: detail.currentVersion?.name || detail.dashboard.name,
  description: detail.currentVersion?.description || detail.dashboard.description,
  activeDatasetId: detail.currentVersion?.activeDatasetId || '',
  widgets: detail.widgets,
  currentVersionNo: detail.dashboard.currentVersionNo,
  currentVersionId: detail.dashboard.currentVersionId,
  updatedAt: detail.dashboard.updateTime,
});

const payload = (document: DashboardDocument) => ({
  name: document.name,
  description: document.description || undefined,
  activeDatasetId: document.activeDatasetId || undefined,
  widgets: document.widgets.map((item) => ({
    widgetKey: item.id,
    analysisId: item.analysisId,
    title: item.title,
    inlineAnalysis: item.inlineAnalysis,
    x: item.x,
    y: item.y,
    w: item.w,
    h: item.h,
    minW: item.minW,
    minH: item.minH,
  })),
});

export const fetchDashboards = async (): Promise<DashboardSummary[]> => {
  const values = unwrap(await HttpUtils.get<DashboardAssetWire[]>(DASHBOARD_API), '查询 Dashboard 列表失败');
  return (values || []).map(summary);
};

export const fetchDashboard = async (dashboardId: string): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(unwrap(
    await HttpUtils.get<DashboardDetailWire>(`${DASHBOARD_API}/${dashboardId}`),
    '查询 Dashboard 详情失败',
  ));

export const createDashboard = async (document: DashboardDocument): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(unwrap(
    await HttpUtils.post<DashboardDetailWire>(DASHBOARD_API, payload(document)),
    '创建 Dashboard 失败',
  ));

export const saveDashboardVersion = async (
  dashboardId: string,
  document: DashboardDocument,
): Promise<DashboardServerDetail> => toDashboardServerDetail(unwrap(
  await HttpUtils.post<DashboardDetailWire>(`${DASHBOARD_API}/${dashboardId}/versions`, payload(document)),
  '保存 DashboardVersion 失败',
));

export const activateDashboardVersion = async (
  dashboardId: string,
  versionNo: number,
): Promise<DashboardServerDetail> => toDashboardServerDetail(unwrap(
  await HttpUtils.post<DashboardDetailWire>(`${DASHBOARD_API}/${dashboardId}/activate/${versionNo}`, {}),
  '切换 DashboardVersion 失败',
));

export const deleteDashboard = async (dashboardId: string): Promise<void> => {
  unwrap(await HttpUtils.delete<boolean>(`${DASHBOARD_API}/${dashboardId}`), '删除 Dashboard 失败');
};
