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

export interface DashboardFilter {
  id: string;
  field: string;
  operator: FilterOperator;
  value: string;
}

export interface DashboardSort {
  field: string;
  direction: SortDirection;
}

export interface DashboardWidgetStyle {
  showLegend: boolean;
  showDataLabels: boolean;
  smooth: boolean;
  showGrid: boolean;
}

export interface DashboardWidget {
  id: string;
  type: ChartType;
  title: string;
  datasetId: string;
  dimensions: string[];
  metrics: MetricBinding[];
  filters: DashboardFilter[];
  sort?: DashboardSort;
  style: DashboardWidgetStyle;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
}

export interface DashboardDocument {
  version: 1;
  id: string;
  name: string;
  activeDatasetId: string;
  widgets: DashboardWidget[];
  updatedAt?: string;
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
