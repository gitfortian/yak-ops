export type DatasetFieldRole = 'dimension' | 'metric';
export type DatasetFieldType = 'string' | 'number' | 'date' | 'datetime' | 'boolean' | 'unknown';
export type Scalar = string | number | boolean | null;

export interface DatasetField {
  key: string;
  label: string;
  physicalName: string;
  dataType: DatasetFieldType;
  role: DatasetFieldRole;
  nullable: boolean;
  description?: string;
}

export interface PublishedDataset {
  id: string;
  name: string;
  description: string;
  status: 'ONLINE' | 'OFFLINE';
  sourceTaskId: string;
  sourceTaskName: string;
  currentVersionNo?: number;
  updatedAt: string;
  fields: DatasetField[];
}

export type ChartType = 'metric' | 'bar' | 'line' | 'pie' | 'table';
export type Aggregation = 'SUM' | 'AVG' | 'COUNT' | 'COUNT_DISTINCT' | 'MAX' | 'MIN';
export type SortDirection = 'asc' | 'desc';
export type FilterOperator = 'eq' | 'neq' | 'contains' | 'gt' | 'gte' | 'lt' | 'lte';

export interface MetricBinding {
  field: string;
  aggregation: Aggregation;
}

export type AnalysisEncodingChannel =
  | 'category'
  | 'value'
  | 'color'
  | 'size'
  | 'label'
  | 'detail'
  | 'tooltip';

/** Semantic field binding used by the visual editor, independent from a concrete chart renderer. */
export interface AnalysisEncodingBinding {
  field: string;
  role: DatasetFieldRole;
  aggregation?: Aggregation;
}

/**
 * Versioned visualization grammar. `dimensions` / `metrics` remain as a compatibility
 * projection for the existing query/render pipeline while the editor evolves around
 * semantic encoding channels.
 */
export interface AnalysisEncoding {
  version: 1;
  category: AnalysisEncodingBinding[];
  value: AnalysisEncodingBinding[];
  color: AnalysisEncodingBinding[];
  size: AnalysisEncodingBinding[];
  label: AnalysisEncodingBinding[];
  detail: AnalysisEncodingBinding[];
  tooltip: AnalysisEncodingBinding[];
}

export interface AnalysisFilter {
  id: string;
  field: string;
  operator: FilterOperator;
  value: string;
}

export interface AnalysisSort {
  field: string;
  direction: SortDirection;
}

export type AnalysisColorPalette = 'yak' | 'ocean' | 'sunset' | 'violet' | 'mono';
export type AnalysisLegendPosition = 'top' | 'right' | 'bottom';
export type AnalysisDataLabelPosition = 'top' | 'inside' | 'outside';
export type AnalysisMetricAlign = 'left' | 'center' | 'right';
export type AnalysisMetricValueSize = 'sm' | 'md' | 'lg';
export type AnalysisTableDensity = 'compact' | 'comfortable' | 'relaxed';

/**
 * Versioned visual appearance grammar. The original four booleans stay required for
 * backwards source compatibility; Phase 7 properties are optional so old persisted
 * Analysis / Dashboard snapshots continue to deserialize without migration.
 */
export interface AnalysisVisualConfig {
  version?: 1;
  showLegend: boolean;
  showDataLabels: boolean;
  smooth: boolean;
  showGrid: boolean;
  palette?: AnalysisColorPalette;
  legendPosition?: AnalysisLegendPosition;
  dataLabelPosition?: AnalysisDataLabelPosition;
  axisLabelRotation?: 0 | 30 | 45;
  lineWidth?: number;
  symbolSize?: number;
  barMaxWidth?: number;
  barRadius?: number;
  pieInnerRadius?: number;
  metricAlign?: AnalysisMetricAlign;
  metricValueSize?: AnalysisMetricValueSize;
  showMetricMeta?: boolean;
  tableDensity?: AnalysisTableDensity;
  stripedRows?: boolean;
}

/** Query + visualization definition that can be reused outside a Dashboard. */
export interface AnalysisSpec {
  type: ChartType;
  datasetId: string;
  /** Compatibility projection of the active category encoding. */
  dimensions: string[];
  /** Compatibility projection of the active value encoding. */
  metrics: MetricBinding[];
  /** Optional for backwards compatibility with existing Analysis / Dashboard snapshots. */
  encoding?: AnalysisEncoding;
  filters: AnalysisFilter[];
  sort?: AnalysisSort;
  style: AnalysisVisualConfig;
  limit?: number;
  timeoutSeconds?: number;
}

export interface AnalysisAsset extends AnalysisSpec {
  id: string;
  name: string;
  description: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Semantic selection emitted by a chart/table for Dashboard interaction routing. */
export interface AnalysisSelection {
  fieldId: string;
  value: Scalar;
  label: string;
  rowIndex: number;
}

export interface DatasetQueryMetric {
  fieldId: string;
  aggregation: Aggregation;
}

export interface DatasetQueryFilter {
  fieldId: string;
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE';
  value: Scalar;
}

export interface DatasetQuerySort {
  fieldId: string;
  aggregation?: Aggregation;
  direction: 'ASC' | 'DESC';
}

export interface DatasetQueryPayload {
  dimensions: string[];
  metrics: DatasetQueryMetric[];
  filters: DatasetQueryFilter[];
  sorts: DatasetQuerySort[];
  limit: number;
  timeoutSeconds: number;
}

export interface DatasetQueryColumnBinding {
  key: string;
  fieldId: string;
  displayName: string;
  dataType: string;
  aggregation?: Aggregation | null;
}

export interface DatasetQueryColumn {
  name: string;
  label: string;
  typeName: string;
  jdbcType: number;
  nullable: boolean;
}

export interface DatasetQueryResult {
  datasetId: string;
  datasetVersionId: string;
  datasetVersionNo: number;
  bindings: DatasetQueryColumnBinding[];
  columns: DatasetQueryColumn[];
  rows: Scalar[][];
  returnedRows: number;
  truncated: boolean;
  elapsedMillis: number;
}
