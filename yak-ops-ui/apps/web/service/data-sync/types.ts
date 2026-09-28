export type DataSyncWriteMode = "APPEND" | "OVERWRITE" | "UPSERT";

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export interface DataSyncRuntimeConfig {
  fetchSize: number;
  readBatchSize: number;
  writeBatchSize: number;
  splitSize?: number;
  sourceParallelism: number;
  timeoutSeconds: number;
}

export interface DataSyncRealtimeConfig {
  checkpointIntervalSeconds: number;
  queueCapacity: number;
  pollBatchSize: number;
  writeBatchSize: number;
  timeoutSeconds: number;
}

export interface DataSyncTaskRecord {
  id: string;
  name: string;
  syncType: DataSyncType | string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  runtimeConfig?: DataSyncRuntimeConfig;
  realtimeConfig?: DataSyncRealtimeConfig;
  definitionVersion: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: DataSyncType;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

export interface DataSyncTaskPageResult {
  bizData: DataSyncTaskRecord[];
  pagination: PaginationInfo;
}

interface DataSyncTaskSaveBase {
  name: string;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  remark?: string;
}

export interface OfflineDataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  syncType: "OFFLINE";
  runtimeConfig: DataSyncRuntimeConfig;
}

export interface RealtimeDataSyncTaskSavePayload extends DataSyncTaskSaveBase {
  syncType: "REALTIME";
  realtimeConfig: DataSyncRealtimeConfig;
}

export type DataSyncTaskSavePayload =
  | OfflineDataSyncTaskSavePayload
  | RealtimeDataSyncTaskSavePayload;

export interface DataSyncMappingPreviewPayload {
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
}

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
  syncType?: DataSyncType | string;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
  runtimeConfig?: DataSyncRuntimeConfig;
  realtimeConfig?: DataSyncRealtimeConfig;
}

export interface DataSyncInstanceRecord {
  id: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
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
  syncType?: DataSyncType;
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
