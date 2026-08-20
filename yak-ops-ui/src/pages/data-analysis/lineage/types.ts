export const LINEAGE_ASSET_TYPES = [
  'TABLE',
  'COLUMN',
  'SQL_TASK',
  'DATASET',
  'DATASET_FIELD',
  'CHART',
  'DASHBOARD',
] as const;

export type LineageAssetType = (typeof LINEAGE_ASSET_TYPES)[number];
export type LineageDirection = 'BOTH' | 'UPSTREAM' | 'DOWNSTREAM';
export type LineageRelationType =
  | 'READS_FROM'
  | 'WRITES_TO'
  | 'DERIVES_FROM'
  | 'CONSUMES'
  | 'CONTAINS';

export interface LineageAsset {
  id: string;
  assetKey: string;
  assetType: LineageAssetType;
  name: string;
  sourceType?: string;
  sourceId?: string;
  parentAssetId?: string;
  dataSourceId?: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  columnName?: string;
  properties?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface LineageRelation {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  relationType: LineageRelationType;
  sourceType?: string;
  sourceId?: string;
  expression?: string;
  confidence?: number;
  version?: string;
  observedAt?: string;
  properties?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface LineageGraph {
  root: LineageAsset;
  direction: LineageDirection;
  depth: number;
  nodes: LineageAsset[];
  relations: LineageRelation[];
}

export const assetTypeLabel: Record<LineageAssetType, string> = {
  TABLE: '数据表',
  COLUMN: '字段',
  SQL_TASK: 'SQL 任务',
  DATASET: 'Dataset',
  DATASET_FIELD: 'Dataset 字段',
  CHART: '图表',
  DASHBOARD: '仪表盘',
};

export const relationTypeLabel: Record<LineageRelationType, string> = {
  READS_FROM: '读取',
  WRITES_TO: '写入',
  DERIVES_FROM: '派生',
  CONSUMES: '消费',
  CONTAINS: '包含',
};
