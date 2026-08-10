export type DevelopmentTaskType = 'SQL' | 'SHELL' | 'HTTP' | 'PYTHON';

export type SqlParameterType =
  | 'STRING'
  | 'INTEGER'
  | 'LONG'
  | 'DOUBLE'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'TIMESTAMP';

export interface SqlParameterDefinition {
  name: string;
  type: SqlParameterType;
  required: boolean;
  defaultValue?: unknown;
}

export interface SqlTaskDefinition {
  id: number;
  name: string;
  description?: string | null;
  projectId?: number | null;
  dataSourceId: number;
  sql: string;
  parameters: SqlParameterDefinition[];
  draftRevision: number;
  publishedVersionId?: number | null;
  latestVersionNo: number;
  createTime?: string;
  updateTime?: string;
}

export interface SqlTaskVersion {
  id: number;
  taskId: number;
  versionNo: number;
  dataSourceId: number;
  sql: string;
  parameters: SqlParameterDefinition[];
  contentDigest: string;
  publishedAt?: string;
}

export interface SqlTaskExecution {
  id: number;
  taskId: number;
  taskVersionId?: number | null;
  taskVersionNo?: number | null;
  dataSourceId: number;
  status: string;
  affectedRows: number;
  output?: Record<string, unknown>;
  errorMessage?: string | null;
  createTime?: string;
  startTime?: string | null;
  finishTime?: string | null;
}

export interface SqlTaskSavePayload {
  name: string;
  description?: string;
  projectId: number;
  dataSourceId: number;
  sql: string;
  parameters: SqlParameterDefinition[];
}

export interface SqlTaskUpdatePayload extends SqlTaskSavePayload {
  baseRevision: number;
}

export interface DevelopmentTaskRow extends SqlTaskDefinition {
  type: 'SQL';
}

export const SQL_PARAMETER_TYPES: SqlParameterType[] = [
  'STRING',
  'INTEGER',
  'LONG',
  'DOUBLE',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'TIMESTAMP',
];

export const TERMINAL_EXECUTION_STATUSES = new Set([
  'SUCCEEDED',
  'FAILED',
  'CANCELED',
  'TIMED_OUT',
  'LOST',
]);
