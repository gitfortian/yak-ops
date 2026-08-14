import type { AnalysisSpec } from '@/components/analysis/model';

export type {
  Aggregation,
  AnalysisAsset,
  AnalysisFilter as DashboardFilter,
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

export interface DashboardDocument {
  version: 1;
  id: string;
  name: string;
  description?: string;
  activeDatasetId: string;
  widgets: DashboardWidget[];
  currentVersionNo?: number;
  currentVersionId?: string;
  updatedAt?: string;
}

export interface DashboardSummary {
  id: string;
  name: string;
  description: string;
  currentVersionId?: string;
  currentVersionNo: number;
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
}
