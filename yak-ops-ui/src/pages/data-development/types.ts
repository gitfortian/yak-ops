export type DevelopmentTaskType = 'SQL' | 'SHELL' | 'HTTP' | 'PYTHON';
export type DevelopmentId = string;

export interface DevelopmentDirectory {
  id: DevelopmentId;
  parentId?: DevelopmentId | null;
  name: string;
  path: string;
  createTime?: string;
  updateTime?: string;
}

export interface CreateDevelopmentDirectoryPayload {
  parentId?: DevelopmentId;
  name: string;
}

export interface DevelopmentNode {
  id: DevelopmentId;
  name: string;
  type: DevelopmentTaskType;
  projectId?: DevelopmentId | null;
  directoryId?: DevelopmentId | null;
  configured: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentTaskType;
  projectId?: DevelopmentId;
  /** 省略表示数据开发根目录。 */
  directoryId?: DevelopmentId;
}

export interface DevelopmentTaskDefinition {
  taskType: DevelopmentTaskType;
  schemaVersion: number;
  content: string;
  configJson: string;
}

export interface DevelopmentTaskDraft {
  nodeId: DevelopmentId;
  definition: DevelopmentTaskDefinition;
  draftRevision: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface SaveDevelopmentTaskDraftPayload extends DevelopmentTaskDefinition {
  baseRevision: number;
}

export interface DevelopmentTaskRevisionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentTaskRevision extends DevelopmentTaskRevisionSummary {
  definition: DevelopmentTaskDefinition;
}

export type DevelopmentTaskExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMEOUT';

export interface DevelopmentTaskRunResult {
  status: DevelopmentTaskExecutionStatus;
  message: string;
  durationMs: number;
  output: Record<string, unknown>;
}

export interface DevelopmentSqlResultColumn {
  name: string;
  label: string;
  typeName?: string;
  jdbcType?: number;
  nullable?: boolean;
}

export interface DevelopmentSqlRunOutput {
  kind?: 'RESULT_SET' | 'UPDATE_COUNT';
  columns?: DevelopmentSqlResultColumn[];
  rows?: unknown[][];
  returnedRows?: number;
  affectedRows?: number;
  truncated?: boolean;
  dataSourceId?: string;
}

export interface DevelopmentTaskExecutionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  taskName: string;
  taskType: DevelopmentTaskType;
  triggerType: string;
  runtimeExecutionId?: string | null;
  status: DevelopmentTaskExecutionStatus;
  operatorName?: string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface DevelopmentTaskExecutionDetail extends DevelopmentTaskExecutionSummary {
  content: string;
  configJson: string;
  output: Record<string, unknown>;
}

export interface DevelopmentTaskExecutionPage {
  records: DevelopmentTaskExecutionSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DevelopmentTaskExecutionQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: DevelopmentTaskExecutionStatus;
  taskType?: DevelopmentTaskType;
  triggerType?: string;
  startTime?: string;
  endTime?: string;
}
