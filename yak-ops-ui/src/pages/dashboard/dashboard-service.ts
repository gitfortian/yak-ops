import type { AnalysisSpec, Scalar } from '@/components/analysis/model';
import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  DashboardDocument,
  DashboardGlobalFilter,
  DashboardInteraction,
  DashboardServerDetail,
  DashboardSummary,
  DashboardVersionDetail,
  DashboardVersionSummary,
  DashboardWidget,
  FilterOperator,
} from './model';

const DASHBOARD_API = '/api/v1/dashboards';

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

interface DashboardGlobalFilterWire {
  filterKey: string;
  name: string;
  operator: 'EQ' | 'NE' | 'CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE';
  defaultValue?: Scalar;
  bindings: Array<{ widgetKey: string; fieldId: string; sortOrder: number }>;
  sortOrder: number;
}

interface DashboardInteractionWire {
  interactionKey: string;
  event: 'SELECT';
  sourceWidgetKey: string;
  sourceFieldId: string;
  targetFilterKey: string;
  sortOrder: number;
}

interface DashboardDetailWire {
  dashboard: DashboardAssetWire;
  currentVersion?: DashboardVersionWire | null;
  versions: DashboardVersionWire[];
  widgets: DashboardWidgetWire[];
  globalFilters?: DashboardGlobalFilterWire[];
  interactions?: DashboardInteractionWire[];
}

interface DashboardVersionDetailWire {
  dashboard: DashboardAssetWire;
  version: DashboardVersionWire;
  widgets: DashboardWidgetWire[];
  globalFilters?: DashboardGlobalFilterWire[];
  interactions?: DashboardInteractionWire[];
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
  publishedVersionId: value.publishedVersionId ? String(value.publishedVersionId) : undefined,
  publishedVersionNo: value.publishedVersionNo || 0,
  publishedTime: value.publishedTime || undefined,
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

const operatorFromWire = (value: DashboardGlobalFilterWire['operator']): FilterOperator => ({
  EQ: 'eq',
  NE: 'neq',
  CONTAINS: 'contains',
  GT: 'gt',
  GTE: 'gte',
  LT: 'lt',
  LTE: 'lte',
}[value] as FilterOperator);

const operatorToWire = (value: FilterOperator): DashboardGlobalFilterWire['operator'] => ({
  eq: 'EQ',
  neq: 'NE',
  contains: 'CONTAINS',
  gt: 'GT',
  gte: 'GTE',
  lt: 'LT',
  lte: 'LTE',
}[value] as DashboardGlobalFilterWire['operator']);

const globalFilter = (value: DashboardGlobalFilterWire): DashboardGlobalFilter => ({
  id: value.filterKey,
  name: value.name,
  operator: operatorFromWire(value.operator),
  defaultValue: value.defaultValue,
  bindings: (value.bindings || []).map((binding) => ({
    widgetId: binding.widgetKey,
    field: binding.fieldId,
  })),
});

const interaction = (value: DashboardInteractionWire): DashboardInteraction => ({
  id: value.interactionKey,
  event: 'select',
  sourceWidgetId: value.sourceWidgetKey,
  sourceField: value.sourceFieldId,
  targetFilterId: value.targetFilterKey,
});

export const toDashboardServerDetail = (value: DashboardDetailWire): DashboardServerDetail => ({
  dashboard: summary(value.dashboard),
  currentVersion: value.currentVersion ? version(value.currentVersion) : undefined,
  versions: (value.versions || []).map(version),
  widgets: (value.widgets || []).map(widget),
  globalFilters: (value.globalFilters || []).map(globalFilter),
  interactions: (value.interactions || []).map(interaction),
});

const toDashboardVersionDetail = (value: DashboardVersionDetailWire): DashboardVersionDetail => ({
  dashboard: summary(value.dashboard),
  version: version(value.version),
  widgets: (value.widgets || []).map(widget),
  globalFilters: (value.globalFilters || []).map(globalFilter),
  interactions: (value.interactions || []).map(interaction),
});

export const toDashboardDocument = (detail: DashboardServerDetail): DashboardDocument => ({
  version: 1,
  id: detail.dashboard.id,
  name: detail.currentVersion?.name || detail.dashboard.name,
  description: detail.currentVersion?.description || detail.dashboard.description,
  activeDatasetId: detail.currentVersion?.activeDatasetId || '',
  widgets: detail.widgets,
  globalFilters: detail.globalFilters,
  interactions: detail.interactions,
  currentVersionNo: detail.dashboard.currentVersionNo,
  currentVersionId: detail.dashboard.currentVersionId,
  publishedVersionNo: detail.dashboard.publishedVersionNo,
  publishedVersionId: detail.dashboard.publishedVersionId,
  publishedAt: detail.dashboard.publishedTime,
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
  globalFilters: document.globalFilters.map((filter) => ({
    filterKey: filter.id,
    name: filter.name,
    operator: operatorToWire(filter.operator),
    defaultValue: filter.defaultValue,
    bindings: filter.bindings.map((binding) => ({
      widgetKey: binding.widgetId,
      fieldId: binding.field,
    })),
  })),
  interactions: document.interactions.map((item) => ({
    interactionKey: item.id,
    event: 'SELECT',
    sourceWidgetKey: item.sourceWidgetId,
    sourceFieldId: item.sourceField,
    targetFilterKey: item.targetFilterId,
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

export const fetchDashboardVersion = async (
  dashboardId: string,
  versionNo: number,
): Promise<DashboardVersionDetail> => toDashboardVersionDetail(unwrap(
  await HttpUtils.get<DashboardVersionDetailWire>(`${DASHBOARD_API}/${dashboardId}/versions/${versionNo}`),
  '查询 DashboardVersion 详情失败',
));

export const fetchPublishedDashboard = async (
  dashboardId: string,
): Promise<DashboardVersionDetail> => toDashboardVersionDetail(unwrap(
  await HttpUtils.get<DashboardVersionDetailWire>(`${DASHBOARD_API}/${dashboardId}/published`),
  '查询已发布 Dashboard 失败',
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
  '保存 Dashboard 草稿失败',
));

export const publishDashboard = async (
  dashboardId: string,
): Promise<DashboardServerDetail> => toDashboardServerDetail(unwrap(
  await HttpUtils.post<DashboardDetailWire>(`${DASHBOARD_API}/${dashboardId}/publish`, {}),
  '发布 Dashboard 失败',
));

export const restoreDashboardVersion = async (
  dashboardId: string,
  versionNo: number,
): Promise<DashboardServerDetail> => toDashboardServerDetail(unwrap(
  await HttpUtils.post<DashboardDetailWire>(`${DASHBOARD_API}/${dashboardId}/restore/${versionNo}`, {}),
  '恢复 DashboardVersion 失败',
));

/** Compatibility helper for callers that still reference the old API name. */
export const activateDashboardVersion = restoreDashboardVersion;

export const deleteDashboard = async (dashboardId: string): Promise<void> => {
  unwrap(await HttpUtils.delete<boolean>(`${DASHBOARD_API}/${dashboardId}`), '删除 Dashboard 失败');
};
