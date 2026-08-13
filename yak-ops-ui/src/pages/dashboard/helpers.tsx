import { BarChart3, ChartLine, ChartPie, Sigma, Table2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { DEFAULT_DASHBOARD, PUBLISHED_DATASETS } from './mock';
import type { Aggregation, ChartType, DashboardDocument, DashboardWidget, FilterOperator, PublishedDataset } from './model';

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

export const loadDashboard = (): DashboardDocument => {
  if (typeof window === 'undefined') return cloneDashboard(DEFAULT_DASHBOARD);
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) return cloneDashboard(DEFAULT_DASHBOARD);
    const parsed = JSON.parse(stored) as DashboardDocument;
    return parsed?.version === 1 && Array.isArray(parsed.widgets)
      ? parsed
      : cloneDashboard(DEFAULT_DASHBOARD);
  } catch {
    return cloneDashboard(DEFAULT_DASHBOARD);
  }
};

export const defaultBindings = (dataset: PublishedDataset) => ({
  dimensions: dataset.fields.filter((field) => field.role === 'dimension').slice(0, 1).map((field) => field.key),
  metrics: dataset.fields.filter((field) => field.role === 'metric').slice(0, 1).map((field) => ({
    field: field.key,
    aggregation: 'SUM' as Aggregation,
  })),
});

export const createWidget = (type: ChartType, dataset: PublishedDataset, y: number): DashboardWidget => {
  const bindings = defaultBindings(dataset);
  const base = {
    id: `${type}-${Date.now()}-${Math.round(Math.random() * 1000)}`,
    type,
    title: `新增${CHART_META[type].label}`,
    datasetId: dataset.id,
    dimensions: type === 'metric' ? [] : bindings.dimensions,
    metrics: bindings.metrics,
    filters: [],
    style: {
      showLegend: type === 'pie',
      showDataLabels: false,
      smooth: type === 'line',
      showGrid: type === 'bar' || type === 'line',
    },
    x: 0,
    y,
    minW: 4,
    minH: 3,
  } satisfies Omit<DashboardWidget, 'w' | 'h'>;

  if (type === 'metric') return { ...base, w: 6, h: 4 };
  if (type === 'table') return { ...base, w: 16, h: 8, minW: 8, minH: 6 };
  return { ...base, w: 10, h: 7, minW: 6, minH: 5 };
};

export const findDataset = (id: string) =>
  PUBLISHED_DATASETS.find((dataset) => dataset.id === id) ?? PUBLISHED_DATASETS[0];
