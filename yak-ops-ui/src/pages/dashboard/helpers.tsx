import {
  applyAnalysisEncoding,
  legacyAnalysisEncoding,
  rebindAnalysisEncoding,
} from '@/components/analysis/encoding';
import type { AnalysisSpec } from '@/components/analysis/model';
import { BarChart3, ChartLine, ChartPie, Sigma, Table2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { DEFAULT_DASHBOARD } from './defaults';
import type {
  Aggregation,
  ChartType,
  DashboardDocument,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
} from './model';

/** Legacy key is read only for one-time migration into the server-side Dashboard domain. */
export const STORAGE_KEY = 'yak-dashboard-designer.v2';
export const GRID_COLUMNS = 24;
export const GRID_ROW_HEIGHT = 28;
export const FIELD_DRAG_MIME = 'application/x-yak-dashboard-field';

export const CHART_META: Record<ChartType, { label: string; description: string; icon: ReactNode }> = {
  metric: { label: '指标卡', description: '展示单个核心指标', icon: <Sigma size={15} /> },
  bar: { label: '柱状图', description: '分类对比与排行', icon: <BarChart3 size={15} /> },
  line: { label: '折线图', description: '趋势与时间序列', icon: <ChartLine size={15} /> },
  pie: { label: '饼图', description: '占比与构成分析', icon: <ChartPie size={15} /> },
  table: { label: '明细表', description: '多维度数据查看', icon: <Table2 size={15} /> },
};

export const AGGREGATION_OPTIONS: Array<{ label: string; value: Aggregation }> = [
  { label: '求和', value: 'SUM' },
  { label: '平均', value: 'AVG' },
  { label: '计数', value: 'COUNT' },
  { label: '去重计数', value: 'COUNT_DISTINCT' },
  { label: '最大值', value: 'MAX' },
  { label: '最小值', value: 'MIN' },
];

export const FILTER_OPERATOR_OPTIONS: Array<{ label: string; value: FilterOperator }> = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'neq' },
  { label: '包含', value: 'contains' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'gte' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'lte' },
];

export const cloneDashboard = (dashboard: DashboardDocument): DashboardDocument =>
  JSON.parse(JSON.stringify(dashboard)) as DashboardDocument;

const legacyWidgetToCurrent = (value: any): DashboardWidget => {
  if (value?.inlineAnalysis || (value?.analysisId && !value?.type)) return value as DashboardWidget;
  if (!value?.type || !value?.datasetId) return value as DashboardWidget;
  const inlineAnalysis: AnalysisSpec = {
    type: value.type,
    datasetId: String(value.datasetId),
    dimensions: Array.isArray(value.dimensions) ? value.dimensions : [],
    metrics: Array.isArray(value.metrics) ? value.metrics : [],
    filters: Array.isArray(value.filters) ? value.filters : [],
    sort: value.sort,
    style: value.style ?? { showLegend: false, showDataLabels: false, smooth: false, showGrid: false },
    limit: value.limit,
    timeoutSeconds: value.timeoutSeconds,
  };
  return {
    id: value.id,
    title: value.title || '未命名图表',
    inlineAnalysis,
    x: Number(value.x ?? 0),
    y: Number(value.y ?? 0),
    w: Number(value.w ?? 10),
    h: Number(value.h ?? 7),
    minW: value.minW,
    minH: value.minH,
  };
};

export const loadDashboard = (): DashboardDocument => {
  if (typeof window === 'undefined') return cloneDashboard(DEFAULT_DASHBOARD);
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) return cloneDashboard(DEFAULT_DASHBOARD);
    const parsed = JSON.parse(stored) as DashboardDocument;
    if (parsed?.version !== 1 || !Array.isArray(parsed.widgets)) return cloneDashboard(DEFAULT_DASHBOARD);
    return {
      ...cloneDashboard(DEFAULT_DASHBOARD),
      ...parsed,
      id: 'dashboard-new',
      currentVersionId: undefined,
      currentVersionNo: undefined,
      widgets: parsed.widgets.map(legacyWidgetToCurrent),
      globalFilters: Array.isArray(parsed.globalFilters) ? parsed.globalFilters : [],
      interactions: Array.isArray(parsed.interactions) ? parsed.interactions : [],
    };
  } catch {
    return cloneDashboard(DEFAULT_DASHBOARD);
  }
};

export const isPersistedDashboard = (value?: string) => Boolean(value && /^\d+$/.test(value));

export const findDataset = (datasets: PublishedDataset[], id?: string) =>
  datasets.find((dataset) => dataset.id === id) ?? datasets[0];

export const defaultBindings = (dataset: PublishedDataset) => ({
  dimensions: dataset.fields.filter((field) => field.role === 'dimension').slice(0, 1).map((field) => field.key),
  metrics: dataset.fields.filter((field) => field.role === 'metric').slice(0, 1).map((field) => ({
    field: field.key,
    aggregation: 'SUM' as Aggregation,
  })),
});

export const createInlineAnalysis = (type: ChartType, dataset: PublishedDataset): AnalysisSpec => {
  const bindings = defaultBindings(dataset);
  const base: AnalysisSpec = {
    type,
    datasetId: dataset.id,
    // Seed semantic category even for metric cards. `applyAnalysisEncoding` keeps the
    // metric query dimensionless while preserving a useful category if the user later
    // switches this chart to bar / line / pie.
    dimensions: bindings.dimensions,
    metrics: bindings.metrics,
    filters: [],
    style: {
      showLegend: type === 'pie',
      showDataLabels: false,
      smooth: type === 'line',
      showGrid: type === 'bar' || type === 'line',
    },
    limit: type === 'table' ? 200 : 500,
    timeoutSeconds: 30,
  };
  return applyAnalysisEncoding(base, legacyAnalysisEncoding(base));
};

export const createWidget = (type: ChartType, dataset: PublishedDataset, y: number): DashboardWidget => ({
  id: `widget-${Date.now()}-${Math.round(Math.random() * 1000)}`,
  title: `新增${CHART_META[type].label}`,
  inlineAnalysis: createInlineAnalysis(type, dataset),
  x: 0,
  y,
  w: type === 'metric' ? 6 : type === 'table' ? 16 : 10,
  h: type === 'metric' ? 4 : type === 'table' ? 8 : 7,
  minW: type === 'metric' ? 4 : type === 'table' ? 8 : 6,
  minH: type === 'metric' ? 3 : type === 'table' ? 6 : 5,
});

const rebindInlineAnalysis = (spec: AnalysisSpec, dataset: PublishedDataset): AnalysisSpec => {
  const sameDataset = spec.datasetId === dataset.id;
  const filters = sameDataset
    ? spec.filters.filter((filter) => dataset.fields.some((field) => field.key === filter.field))
    : [];
  const sort = sameDataset && spec.sort && dataset.fields.some((field) => field.key === spec.sort?.field)
    ? spec.sort
    : undefined;

  if (!sameDataset) {
    const fresh = createInlineAnalysis(spec.type, dataset);
    return {
      ...fresh,
      style: { ...spec.style },
      filters,
      sort,
      limit: spec.type === 'table' ? 200 : spec.limit,
      timeoutSeconds: spec.timeoutSeconds,
    };
  }

  const rebound = rebindAnalysisEncoding(spec, dataset);
  return { ...rebound, filters, sort };
};

export const reconcileDashboard = (
  dashboard: DashboardDocument,
  datasets: PublishedDataset[],
): DashboardDocument => {
  if (!datasets.length) return dashboard;
  const activeDataset = findDataset(datasets, dashboard.activeDatasetId) ?? datasets[0];
  return {
    ...dashboard,
    activeDatasetId: activeDataset.id,
    widgets: dashboard.widgets.map((item) => {
      if (item.analysisId || !item.inlineAnalysis) return item;
      const widgetDataset = datasets.find((dataset) => dataset.id === item.inlineAnalysis?.datasetId) ?? activeDataset;
      return { ...item, inlineAnalysis: rebindInlineAnalysis(item.inlineAnalysis, widgetDataset) };
    }),
  };
};