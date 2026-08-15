import type {
  AnalysisSpec,
  FilterOperator,
  Scalar,
} from '@/components/analysis/model';

export type {
  Aggregation,
  AnalysisAsset,
  AnalysisFilter as DashboardFilter,
  AnalysisSelection,
  AnalysisSort as DashboardSort,
  AnalysisVisualConfig as DashboardWidgetStyle,
  ChartType,
  DatasetField,
  DatasetFieldRole,
  DatasetFieldType,
  DatasetQueryColumn,
  DatasetQueryColumnBinding,
  DatasetQueryFilter,
  DatasetQueryMetric,
  DatasetQueryPayload,
  DatasetQueryResult,
  DatasetQuerySort,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  Scalar,
  SortDirection,
} from '@/components/analysis/model';

/** Dashboard owns placement plus an Analysis reference or a dashboard-local inline analysis. */
export interface DashboardWidget {
  id: string;
  analysisId?: string;
  title?: string;
  inlineAnalysis?: AnalysisSpec;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
}

export type DashboardWidgetEditorModel = DashboardWidget & AnalysisSpec & { title: string };

export interface DashboardGlobalFilterBinding {
  widgetId: string;
  field: string;
}

/** Versioned Dashboard filter definition. Current user-entered values are runtime-only. */
export interface DashboardGlobalFilter {
  id: string;
  name: string;
  operator: FilterOperator;
  defaultValue?: Scalar;
  bindings: DashboardGlobalFilterBinding[];
}

/** Versioned routing rule: selecting a source dimension writes into a global filter. */
export interface DashboardInteraction {
  id: string;
  event: 'select';
  sourceWidgetId: string;
  sourceField: string;
  targetFilterId: string;
}

export interface DashboardDocument {
  version: 1;
  id: string;
  name: string;
  description?: string;
  activeDatasetId: string;
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  /** Current immutable server-side draft snapshot. */
  currentVersionNo?: number;
  currentVersionId?: string;
  /** Reader-facing published snapshot. It changes only when Publish is invoked. */
  publishedVersionNo?: number;
  publishedVersionId?: string;
  publishedAt?: string;
  updatedAt?: string;
}

export interface DashboardSummary {
  id: string;
  name: string;
  description: string;
  currentVersionId?: string;
  currentVersionNo: number;
  publishedVersionId?: string;
  publishedVersionNo: number;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DashboardVersionSummary {
  id: string;
  dashboardId: string;
  versionNo: number;
  name: string;
  description: string;
  activeDatasetId?: string;
  createTime?: string;
}

export interface DashboardServerDetail {
  dashboard: DashboardSummary;
  currentVersion?: DashboardVersionSummary;
  versions: DashboardVersionSummary[];
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}

export interface DashboardVersionDetail {
  dashboard: DashboardSummary;
  version: DashboardVersionSummary;
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}
