export type DevelopmentTaskType = 'SQL' | 'SHELL' | 'HTTP' | 'PYTHON';
export type DevelopmentOutputNodeType = 'DATASET' | 'DATA_SERVICE';
export type DevelopmentNodeType = DevelopmentTaskType | DevelopmentOutputNodeType;
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

export interface DevelopmentResourceNodeBase {
  id: DevelopmentId;
  name: string;
  projectId?: DevelopmentId | null;
  directoryId?: DevelopmentId | null;
  configured: boolean;
  createTime?: string;
  updateTime?: string;
  updatedBy?: string | null;
  pendingPublish?: boolean;
}

/** Authoring/executable node managed by the existing task editor lifecycle. */
export interface DevelopmentNode extends DevelopmentResourceNodeBase {
  type: DevelopmentTaskType;
}

/** Standalone output resource edited inside data development. */
export interface DevelopmentOutputNode extends DevelopmentResourceNodeBase {
  type: DevelopmentOutputNodeType;
}

export type DevelopmentResourceNode = DevelopmentNode | DevelopmentOutputNode;

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentNodeType;
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

export type DevelopmentSqlLineagePreviewStatus =
  | 'SUCCESS'
  | 'PARTIAL'
  | 'UNRESOLVED'
  | 'FAILED';

export interface DevelopmentSqlLineagePreviewAsset {
  id: string;
  assetKey: string;
  assetType: 'TABLE' | 'SQL_TASK';
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
}

export interface DevelopmentSqlLineagePreviewRelation {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  relationType: 'READS_FROM' | 'WRITES_TO';
  sourceType?: string;
  sourceId?: string;
  expression?: string;
  properties?: Record<string, unknown>;
}

export interface DevelopmentSqlLineagePreviewGraph {
  root: DevelopmentSqlLineagePreviewAsset;
  direction: 'BOTH';
  depth: number;
  nodes: DevelopmentSqlLineagePreviewAsset[];
  relations: DevelopmentSqlLineagePreviewRelation[];
}

export interface DevelopmentSqlLineageColumnMapping {
  sourceTable: string;
  sourceColumn: string;
  targetTable?: string | null;
  targetColumn: string;
  mappingKind: 'IDENTITY' | 'TRANSFORMATION' | 'AGGREGATION';
  expression?: string | null;
  outputOrdinal: number;
  sourceOrdinal: number;
}

export interface DevelopmentSqlLineagePreview {
  status: DevelopmentSqlLineagePreviewStatus;
  dataSourceId: string;
  statementCount: number;
  inputTableCount: number;
  outputTableCount: number;
  columnMappingCount: number;
  candidateOutputColumnCount: number;
  unresolvedColumnReferenceCount: number;
  parseError?: string | null;
  columnParseError?: string | null;
  graph: DevelopmentSqlLineagePreviewGraph;
  columnMappings: DevelopmentSqlLineageColumnMapping[];
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

export type DevelopmentReleaseStatus = 'ONLINE' | 'OFFLINE' | 'DISABLED';

export interface DevelopmentReleaseSummary {
  assetId: DevelopmentId;
  nodeId: DevelopmentId;
  taskName: string;
  taskType: DevelopmentTaskType;
  status: DevelopmentReleaseStatus;
  currentRevisionId: DevelopmentId;
  currentRevisionNo: number;
  latestRevisionNo: number;
  hasNewerRevision: boolean;
  checksum: string;
  revisionCreateTime?: string | null;
  updateTime?: string | null;
}

export interface DevelopmentReleasePage {
  records: DevelopmentReleaseSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
  onlineCount: number;
  offlineCount: number;
  disabledCount: number;
}

export interface DevelopmentReleaseDetail {
  release: DevelopmentReleaseSummary;
  currentRevision: DevelopmentTaskRevision;
  revisions: DevelopmentTaskRevisionSummary[];
}

export interface DevelopmentReleaseQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: DevelopmentReleaseStatus | 'ALL';
  taskType?: DevelopmentTaskType;
}
