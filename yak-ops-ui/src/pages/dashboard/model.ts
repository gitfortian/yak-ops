export type DatasetFieldRole = 'dimension' | 'metric';
export type DatasetFieldType = 'string' | 'number' | 'date';
export type Scalar = string | number;

export interface DatasetField {
  key: string;
  label: string;
  dataType: DatasetFieldType;
  role: DatasetFieldRole;
  description?: string;
}

export interface PublishedDataset {
  id: string;
  name: string;
  description: string;
  sourceTaskId: string;
  sourceTaskName: string;
  updatedAt: string;
  fields: DatasetField[];
  rows: Array<Record<string, Scalar>>;
}

export type ChartType = 'metric' | 'bar' | 'line' | 'pie' | 'table';
export type Aggregation = 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN';
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

export interface AggregatedRow {
  key: string;
  label: string;
  values: Record<string, number>;
  raw: Record<string, Scalar>;
}
