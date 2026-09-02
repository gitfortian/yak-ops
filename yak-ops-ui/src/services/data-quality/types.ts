export type QualityResourceId = number | string;

export type RuleScope = 'TABLE' | 'COLUMN';
export type RuleType =
  | 'TABLE_ROW_COUNT'
  | 'COLUMN_NOT_NULL'
  | 'COLUMN_UNIQUE'
  | 'COLUMN_RANGE'
  | 'COLUMN_ENUM'
  | 'CUSTOM_SQL';
export type ComparisonOperator = 'GT' | 'GTE' | 'EQ' | 'LTE' | 'LT' | 'BETWEEN';
export type TemplateSource = 'SYSTEM' | 'CUSTOM';
export type CustomCheckType = 'NUMERIC';
export type CustomCheckMethod = 'FIXED_VALUE';
export type ExecutionStatus = 'WAITING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type CheckResult =
  | 'PASSED'
  | 'NOT_PASSED'
  | 'ERROR'
  | 'RUNNING'
  | 'NOT_RUN';
export type TriggerType = 'MANUAL' | 'SCHEDULE';
export type RunMode = 'MANUAL' | 'SCHEDULE';
export type ScheduleFrequency = 'DAILY' | 'WEEKLY' | 'CRON';
export type ScheduleWeekday =
  | 'MON'
  | 'TUE'
  | 'WED'
  | 'THU'
  | 'FRI'
  | 'SAT'
  | 'SUN';
export type RuleFailureAction = 'CONTINUE' | 'STOP';
export type NotifyChannel = 'MESSAGE' | 'EMAIL' | 'WEBHOOK';
export type AlertLevel = 'WARNING' | 'CRITICAL';

/** Compatibility envelope retained for data-quality pages pending migration. */
export interface CommonApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

export interface TemplateView {
  id: number;
  code: string;
  name: string;
  description?: string;
  ruleType: RuleType;
  scope: RuleScope;
  dimension: string;
  parameterSchema: string;
  builtin: boolean;
  enabled: boolean;
  ruleCount: number;
  sortOrder: number;
  folderId?: number;
  folderName?: string;
  templateSql?: string;
  setFlag?: string;
  checkType?: CustomCheckType;
  checkMethod?: CustomCheckMethod;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  source?: TemplateSource;
}

export interface TemplateSummary {
  total: number;
  systemTotal: number;
  customTotal: number;
  dimensions: Record<string, number>;
}

export interface TemplateListView {
  records: TemplateView[];
  summary: TemplateSummary;
}

export interface TemplateFolderView {
  id: number;
  parentId?: number;
  name: string;
  sortOrder: number;
  templateCount: number;
  childCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveTemplateFolderPayload {
  name: string;
  parentId?: number;
}

export interface SaveCustomTemplatePayload {
  name: string;
  description?: string;
  dimension: string;
  folderId?: number;
  setFlag?: string;
  checkType: CustomCheckType;
  checkMethod: CustomCheckMethod;
  customSql: string;
  defaultOperator: ComparisonOperator;
  defaultThreshold: number;
  defaultThresholdEnd?: number;
}

export interface CopyCustomTemplatePayload {
  name: string;
  folderId?: number;
}

export interface SaveRulePayload {
  templateId: number;
  name: string;
  columnName?: string;
  operator?: ComparisonOperator;
  threshold?: number;
  thresholdEnd?: number;
  enumValues?: string[];
  customSql?: string;
  enabled?: boolean;
}

/**
 * New monitor editors use scheduleEnabled + cronExpression.
 * The friendly runMode/frequency fields remain optional for older API consumers.
 */
export interface MonitorSettingsPayload {
  scheduleEnabled?: boolean;
  cronExpression?: string;
  ruleFailureAction: RuleFailureAction;
  notifyEnabled: boolean;
  notifyChannel: NotifyChannel;
  notifyTarget?: string;
  alertLevel: AlertLevel;
  runMode?: RunMode;
  scheduleFrequency?: ScheduleFrequency;
  scheduleTime?: string;
  scheduleWeekday?: ScheduleWeekday;
}

export interface MonitorSettingsView extends MonitorSettingsPayload {
  nextRunTime?: string;
}

export interface SaveMonitorPayload {
  name: string;
  description?: string;
  dataSourceId: number;
  dataSourceName: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  whereClause?: string;
  owner: string;
  enabled?: boolean;
  settings: MonitorSettingsPayload;
  rules: SaveRulePayload[];
}

export interface RuleView extends SaveRulePayload {
  id: number;
  monitorId: number;
  templateCode: string;
  ruleType: RuleType;
  scope: RuleScope;
  dimension: string;
  sortOrder: number;
  enabled: boolean;
}

export interface MonitorListItem {
  id: number;
  name: string;
  description?: string;
  dataSourceId: number;
  dataSourceName: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  owner: string;
  enabled: boolean;
  ruleCount: number;
  lastResult: CheckResult;
  lastExecutionNo?: string;
  lastRunTime?: string;
  createTime: string;
  updateTime: string;
}

export interface MonitorView extends Omit<MonitorListItem, 'ruleCount'> {
  whereClause?: string;
  rules: RuleView[];
}

export interface MonitorPageView {
  records: MonitorListItem[];
  total: number;
  current: number;
  pageSize: number;
}

export interface TableMonitorSummary {
  tableName: string;
  monitorId?: number;
  monitorName?: string;
  monitorCount: number;
  ruleCount: number;
  lastResult: CheckResult;
  lastRunTime?: string;
}

export interface TableAssetView {
  id: number;
  dataSourceId: number;
  dataSourceName: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  tableType?: string;
  remarks?: string;
  monitorId?: number;
  monitorName?: string;
  monitorCount: number;
  ruleCount: number;
  lastResult: CheckResult;
  lastRunTime?: string;
  registeredBy?: string;
  registeredAt?: string;
}

export interface TableAssetPageView {
  records: TableAssetView[];
  total: number;
  current: number;
  pageSize: number;
}

export interface TableCandidateView {
  dataSourceId: number;
  dataSourceName: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  tableType?: string;
  remarks?: string;
  registered: boolean;
  assetId?: number;
  monitorCount: number;
}

export interface TableCandidatePageView {
  records: TableCandidateView[];
  total: number;
  current: number;
  pageSize: number;
}

export interface RegisterTablesPayload {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
  tableNames: string[];
}

export interface RegisterTablesView {
  createdCount: number;
  skippedCount: number;
}

export interface RunView {
  executionNo: string;
  executionStatus: ExecutionStatus;
  checkResult: CheckResult;
}

export interface ExecutionRuleView {
  id: number;
  ruleId: number;
  ruleName: string;
  templateCode: string;
  ruleType: RuleType;
  columnName?: string;
  checkResult: CheckResult;
  metricValue?: string;
  expectedValue?: string;
  executedSql?: string;
  errorMessage?: string;
  durationMs?: number;
  createdAt?: string;
}

export interface ExecutionView {
  id: number;
  executionNo: string;
  monitorId: number;
  monitorName: string;
  dataSourceId: number;
  dataSourceName: string;
  databaseName?: string;
  schemaName?: string;
  tableName: string;
  objectName?: string;
  triggerType: TriggerType;
  executionStatus: ExecutionStatus;
  checkResult: CheckResult;
  totalRules: number;
  passedRules: number;
  failedRules: number;
  errorRules: number;
  operator?: string;
  queuedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  errorMessage?: string;
  rules?: ExecutionRuleView[];
}

export interface ExecutionPageView {
  records: ExecutionView[];
  total: number;
  current: number;
  pageSize: number;
}

export interface QualityMonitorPageQuery {
  current?: number;
  pageSize?: number;
  keyword?: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  enabled?: boolean;
  lastResult?: CheckResult;
}

export interface QualityExecutionPageQuery {
  current?: number;
  pageSize?: number;
  monitorId?: number;
  triggerType?: TriggerType;
  executionStatus?: ExecutionStatus;
  checkResult?: CheckResult;
}

export interface QualityTableAssetPageQuery {
  current?: number;
  pageSize?: number;
  keyword?: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  monitored?: boolean;
}

export interface QualityTableCandidateQuery {
  current?: number;
  pageSize?: number;
  keyword?: string;
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
}

export interface QualityTableSummaryQuery {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
}

export interface QualityTemplateListQuery {
  keyword?: string;
  source?: TemplateSource;
  dimension?: string;
  folderId?: number;
}

export interface CatalogColumn {
  name: string;
  type?: string;
  remarks?: string;
}

export interface MonitorWorkspaceStats {
  ruleCount: number;
  enabledRuleCount: number;
  executionCount: number;
  issueExecutionCount: number;
  latestExecutionTime?: string;
}

export interface MonitorWorkspaceView {
  monitor: MonitorView;
  settings: MonitorSettingsView;
  stats: MonitorWorkspaceStats;
}

export interface MonitorReportOverview {
  totalRules: number;
  enabledRules: number;
  executedRules: number;
  issueRules: number;
  errorRules: number;
  passRate: number;
}

export interface DimensionReportView {
  dimension: string;
  total: number;
  passed: number;
  notPassed: number;
  errors: number;
  passRate: number;
}

export interface TrendPointView {
  date: string;
  dimension: string;
  total: number;
  passed: number;
  issues: number;
  passRate: number;
}

export interface ColumnReportView {
  columnName: string;
  dimension: string;
  total: number;
  passed: number;
  issues: number;
  passRate: number;
}

export interface MonitorReportView {
  overview: MonitorReportOverview;
  dimensions: DimensionReportView[];
  trends: TrendPointView[];
  columns: ColumnReportView[];
}

export interface OperationLogView {
  id: string;
  operator: string;
  operationTime: string;
  actionType: string;
  content: string;
}

export interface OperationLogPageView {
  records: OperationLogView[];
  total: number;
  current: number;
  pageSize: number;
}
