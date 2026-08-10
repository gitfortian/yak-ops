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

export interface DevelopmentDirectory {
  id: number;
  parentId?: number | null;
  name: string;
  path: string;
  createTime?: string;
  updateTime?: string;
}

export interface CreateDevelopmentDirectoryPayload {
  parentId?: number;
  name: string;
}

export interface DevelopmentNode {
  id: number;
  name: string;
  type: DevelopmentTaskType;
  projectId?: number | null;
  directoryId?: number | null;
  configured: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentTaskType;
  projectId?: number;
  /** 0 或省略表示数据开发根目录。 */
  directoryId?: number;
}

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
  directoryId?: number | null;
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
  projectId?: number;
  /** 0 表示数据开发根目录。 */
  directoryId: number;
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
