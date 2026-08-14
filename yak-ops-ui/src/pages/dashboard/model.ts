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

/**
 * Dashboard only owns placement and a reference (or a temporary inline analysis draft).
 * Reusable query/visual definitions live in Analysis assets.
 */
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

/** UI-only flattened view used by the legacy Dashboard configuration controls. */
export type DashboardWidgetEditorModel = DashboardWidget & AnalysisSpec & { title: string };

export interface DashboardDocument {
  version: 1;
  id: string;
  name: string;
  activeDatasetId: string;
  widgets: DashboardWidget[];
  updatedAt?: string;
}
