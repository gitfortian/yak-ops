export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface DataSyncRuntimeConfig {
  fetchSize: number;
  readBatchSize: number;
  writeBatchSize: number;
  splitSize?: number;
  sourceParallelism: number;
  timeoutSeconds: number;
}

export interface DataSyncTaskRecord {
  id: string;
  name: string;
  syncType: "OFFLINE" | string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  runtimeConfig: DataSyncRuntimeConfig;
  definitionVersion: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: "OFFLINE";
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

export interface DataSyncTaskPageResult {
  bizData: DataSyncTaskRecord[];
  pagination: PaginationInfo;
}

export interface DataSyncTaskSavePayload {
  name: string;
  syncType: "OFFLINE";
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  runtimeConfig: DataSyncRuntimeConfig;
  remark?: string;
}

export type DataSyncMappingPreviewPayload = Pick<
  DataSyncTaskSavePayload,
  | "sourceDataSourceId"
  | "sourceDatabase"
  | "sourceSchema"
  | "sourceTable"
  | "targetDataSourceId"
  | "targetDatabase"
  | "targetSchema"
  | "targetTable"
>;

export interface DataSyncFieldMapping {
  sourceName: string;
  sourceType?: string;
  targetName?: string;
  targetType?: string;
  compatible: boolean;
  message?: string;
}

export interface DataSyncMappingPreview {
  compatible: boolean;
  mappings: DataSyncFieldMapping[];
}

export type DataSyncInstanceStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELED"
  | "LOST"
  | string;

export interface DataSyncEndpointSnapshot {
  dataSourceId: string;
  dataSourceName?: string;
  dataSourceType?: string;
  database?: string;
  schema?: string;
  table: string;
}

export interface DataSyncDefinitionSnapshot {
  taskId: string;
  taskName: string;
  taskVersion: number;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
  runtimeConfig: DataSyncRuntimeConfig;
}

export interface DataSyncInstanceRecord {
  id: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
  triggerType: "MANUAL" | "SCHEDULE" | "RETRY" | string;
  status: DataSyncInstanceStatus;
  readRows: number;
  writeRows: number;
  startTime?: string;
  finishTime?: string;
  errorCode?: number;
  errorMessage?: string;
  definitionSnapshot?: DataSyncDefinitionSnapshot;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncInstancePageParams {
  pageNo: number;
  pageSize: number;
  taskId?: string;
  keyword?: string;
  status?: DataSyncInstanceStatus;
  triggerType?: string;
  startTimeStart?: string;
  startTimeEnd?: string;
}

export interface DataSyncInstancePageResult {
  bizData: DataSyncInstanceRecord[];
  pagination: PaginationInfo;
}
