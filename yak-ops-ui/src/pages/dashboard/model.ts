import type {
  AnalysisSpec,
  FilterOperator,
  Scalar,
} from '@/components/analysis/model';

export type {
  Aggregation,
  AnalysisAsset,
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisEncodingChannel,
  AnalysisFilter as DashboardFilter,
  AnalysisSelection,
  AnalysisSort as DashboardSort,
  AnalysisThemeTokens,
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

export type DashboardWidgetClickAction = 'none' | 'drill' | 'dashboard' | 'yak';
export type DashboardThemePresetId =
  | 'yak-light'
  | 'yak-dark'
  | 'ocean-night'
  | 'graphite'
  | 'mist-blue';

export interface DashboardThemeCanvas {
  backgroundColor?: string;
}

export interface DashboardThemeComponent {
  backgroundColor?: string;
  textColor?: string;
}

export interface DashboardThemeChart {
  palette?: string[];
  textColor?: string;
  axisColor?: string;
  gridColor?: string;
}

/** Dashboard-level visual defaults. Preset values are resolved on the client; fields are explicit overrides. */
export interface DashboardTheme {
  presetId?: DashboardThemePresetId;
  canvas?: DashboardThemeCanvas;
  component?: DashboardThemeComponent;
  chart?: DashboardThemeChart;
}

/**
 * Dashboard-local direct chart linkage. The source widget owns the rule; selecting its
 * source field creates a runtime EQ filter on the mapped target widget field.
 */
export interface DashboardCrossFilterRule {
  id: string;
  sourceField: string;
  targetWidgetId: string;
  targetField: string;
}

/** Dashboard-only behavior persisted together with the inline Analysis snapshot. */
export interface DashboardWidgetBehavior {
  /** Direct chart-to-chart filters introduced in BI Phase 11. */
  crossFilters?: DashboardCrossFilterRule[];
  clickAction?: DashboardWidgetClickAction;
  /** Ordered hierarchy, e.g. province -> city -> store. */
  drillFields?: string[];
  /** Dashboard asset id used by dashboard-to-dashboard navigation. */
  targetDashboardId?: string;
  /** Yak internal route such as /workflow/instances. */
  targetPath?: string;
  /** Query-string key used by Yak page navigation. */
  queryParam?: string;
}

/**
 * Dashboard inline analyses intentionally extend the reusable AnalysisSpec with
 * dashboard-local interaction metadata. The backend already versions this JSON
 * blob as part of the immutable DashboardWidget snapshot.
 */
export interface DashboardInlineAnalysisSpec extends AnalysisSpec {
  dashboardBehavior?: DashboardWidgetBehavior;
}

/** Runtime-only drill breadcrumb entry. It never participates in Dirty state. */
export interface DashboardDrillStep {
  field: string;
  value: Scalar;
  label: string;
}

/** Dashboard owns placement plus an Analysis reference or a dashboard-local inline analysis. */
export interface DashboardWidget {
  id: string;
  analysisId?: string;
  title?: string;
  inlineAnalysis?: DashboardInlineAnalysisSpec;
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
  theme?: DashboardTheme;
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
  theme?: DashboardTheme;
  versions: DashboardVersionSummary[];
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}

export interface DashboardVersionDetail {
  dashboard: DashboardSummary;
  version: DashboardVersionSummary;
  theme?: DashboardTheme;
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}
