export type DataSyncWriteMode = "APPEND" | "OVERWRITE" | "UPSERT";

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export type DataSyncType = "OFFLINE" | "REALTIME";

export type DataSyncTaskStatus = "UNPUBLISHED" | "PUBLISHED";

export type DataSyncDesiredState = "STOPPED" | "RUNNING";

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

export interface DataSyncRetryPolicy {
  maxAttempts: number;
  backoffSeconds: number;
}

export interface DataSyncTaskRecord {
  id: string;
  name: string;
  syncType: DataSyncType | string;
  status: DataSyncTaskStatus | string;
  desiredState?: DataSyncDesiredState | string;
  writeMode: DataSyncWriteMode | string;
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
  retryPolicy?: DataSyncRetryPolicy;
  definitionVersion: number;
  remark?: string;
  updateBy?: string;
  scheduleCronExpression?: string;
  scheduleTimeZone?: string;
  scheduleEnabled?: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncTaskPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  syncType?: DataSyncType;
  status?: DataSyncTaskStatus;
  sourceDataSourceId?: string;
  targetDataSourceId?: string;
}

export interface DataSyncTaskPageResult {
  bizData: DataSyncTaskRecord[];
  pagination: PaginationInfo;
}

export interface DataSyncScheduleRecord {
  id: string;
  taskId: string;
  cronExpression: string;
  timeZone: string;
  enabled: boolean;
  nextFireTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSyncScheduleSavePayload {
  cronExpression: string;
  timeZone: string;
}

export interface DataSyncSchedulePreview {
  cronExpression: string;
  timeZone: string;
  nextFireTimes: string[];
}

export interface DataSyncTaskOperationRecord {
  id: string;
  name: string;
  syncType: DataSyncType | string;
  desiredState?: DataSyncDesiredState | string;
  definitionVersion: number;
  retryPolicy?: DataSyncRetryPolicy;
  latestInstance?: DataSyncInstanceRecord;
  schedule?: DataSyncScheduleRecord;
}

export interface DataSyncTaskOperationPageResult {
  bizData: DataSyncTaskOperationRecord[];
  pagination: PaginationInfo;
}

interface DataSyncTaskSaveBase {
  name: string;
  writeMode?: DataSyncWriteMode;
  sourceDataSourceId: string;
  sourceDatabase?: string;
  sourceSchema?: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase?: string;
  targetSchema?: string;
  targetTable: string;
  retryPolicy?: DataSyncRetryPolicy;
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
  | "RETRY_WAITING"
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
  writeMode?: DataSyncWriteMode | string;
  source: DataSyncEndpointSnapshot;
  target: DataSyncEndpointSnapshot;
  runtimeConfig?: DataSyncRuntimeConfig;
  realtimeConfig?: DataSyncRealtimeConfig;
  retryPolicy?: DataSyncRetryPolicy;
}

export interface DataSyncInstanceRecord {
  id: string;
  taskId: string;
  taskName: string;
  taskVersion: number;
  syncType: DataSyncType | string;
  triggerType: "MANUAL" | "SCHEDULE" | "RETRY" | "AUTO_RECOVERY" | string;
  status: DataSyncInstanceStatus;
  maxAttempts?: number;
  backoffSeconds?: number;
  currentAttempt?: number;
  nextRetryTime?: string;
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

export interface DataSyncAttemptRecord {
  id: string;
  executionId: string;
  attemptNo: number;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED" | "LOST" | string;
  readRows: number;
  writeRows: number;
  startTime?: string;
  finishTime?: string;
  errorCode?: number;
  errorMessage?: string;
  createTime?: string;
  updateTime?: string;
}

export type DataSyncExecutionEventLevel = "INFO" | "WARN" | "ERROR" | string;

export type DataSyncExecutionEventType =
  | "EXECUTION_STARTED"
  | "ATTEMPT_STARTED"
  | "SOURCE_READY"
  | "TARGET_READY"
  | "ATTEMPT_SUCCEEDED"
  | "ATTEMPT_FAILED"
  | "RETRY_WAITING"
  | "EXECUTION_SUCCEEDED"
  | "EXECUTION_FAILED"
  | "EXECUTION_CANCELED"
  | "EXECUTION_LOST"
  | "AUTO_RECOVERY_STARTED"
  | string;

export interface DataSyncExecutionEventRecord {
  id: string;
  executionId: string;
  attemptId?: string;
  level: DataSyncExecutionEventLevel;
  eventType: DataSyncExecutionEventType;
  message: string;
  createTime?: string;
}

export type DataSyncOperationsRange = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS";

export interface DataSyncOperationsDashboardPayload {
  syncType: DataSyncType;
  range: DataSyncOperationsRange;
}

export interface DataSyncOperationsSummary {
  executionCount: number;
  succeededCount: number;
  failedCount: number;
  lostCount: number;
  abnormalTaskCount: number;
  currentActiveTaskCount: number;
  autoRecoveryCount: number;
  readRows: number;
  writeRows: number;
  averageDurationMillis: number;
}

export interface DataSyncOperationsTrendPoint {
  bucketStart: string;
  executionCount: number;
  succeededCount: number;
  failedCount: number;
  lostCount: number;
  autoRecoveryCount: number;
  readRows: number;
  writeRows: number;
  averageDurationMillis: number;
}

export interface DataSyncOperationsStatusMetric {
  status: DataSyncInstanceStatus;
  count: number;
}

export interface DataSyncOperationsFailureRank {
  taskId: string;
  taskName: string;
  failedCount: number;
  lostCount: number;
  abnormalCount: number;
  latestFailureTime?: string;
}

export interface DataSyncOperationsDashboard {
  syncType: DataSyncType | string;
  range: DataSyncOperationsRange | string;
  rangeStart: string;
  rangeEnd: string;
  summary: DataSyncOperationsSummary;
  trend: DataSyncOperationsTrendPoint[];
  statusDistribution: DataSyncOperationsStatusMetric[];
  failureRanking: DataSyncOperationsFailureRank[];
}
