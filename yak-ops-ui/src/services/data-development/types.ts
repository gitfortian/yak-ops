export type DevelopmentTaskType =
  | 'SQL'
  | 'SHELL'
  | 'HTTP'
  | 'PYTHON'
  | 'JAVA';
export type DevelopmentOutputNodeType = 'DATASET' | 'DATA_SERVICE';
export type DevelopmentNodeType =
  | DevelopmentTaskType
  | DevelopmentOutputNodeType;
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

export interface DevelopmentNode extends DevelopmentResourceNodeBase {
  type: DevelopmentTaskType;
}

export interface DevelopmentOutputNode extends DevelopmentResourceNodeBase {
  type: DevelopmentOutputNodeType;
}

export type DevelopmentResourceNode =
  | DevelopmentNode
  | DevelopmentOutputNode;

export interface CreateDevelopmentNodePayload {
  name: string;
  type: DevelopmentNodeType;
  /** Omit to create the node in the data-development root. */
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

export interface SaveDevelopmentTaskDraftPayload
  extends DevelopmentTaskDefinition {
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

export interface DevelopmentTaskRevision
  extends DevelopmentTaskRevisionSummary {
  definition: DevelopmentTaskDefinition;
}

export interface DevelopmentTaskExecutionSubmission {
  id: DevelopmentId;
  status: string;
  runtimeExecutionId?: string | null;
}

export interface DevelopmentTaskExecutionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  taskName: string;
  taskType: DevelopmentTaskType;
  triggerType: string;
  runtimeExecutionId?: string | null;
  retryOfExecutionId?: DevelopmentId | null;
  status: string;
  operatorName?: string | null;
  durationMs?: number | null;
  errorMessage?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface DevelopmentTaskExecutionDetail
  extends DevelopmentTaskExecutionSummary {
  schemaVersion: number;
  content: string;
  configJson: string;
  outputJson?: string | null;
}

export interface DevelopmentTaskExecutionPage {
  records: DevelopmentTaskExecutionSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DevelopmentTaskExecutionQuery {
  keyword?: string;
  status?: string;
  taskType?: string;
  triggerType?: string;
  startTime?: string;
  endTime?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface DevelopmentReleaseSummary {
  assetId: DevelopmentId;
  sourceRef: string;
  name: string;
  taskType: string;
  status: string;
  currentRevisionId: DevelopmentId;
  currentRevisionNo: number;
  updateTime?: string | null;
}

export interface DevelopmentReleaseDetail {
  asset: DevelopmentReleaseSummary;
  revisions: DevelopmentTaskRevisionSummary[];
}

export interface DevelopmentReleasePage {
  records: DevelopmentReleaseSummary[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DevelopmentReleaseQuery {
  keyword?: string;
  status?: string;
  taskType?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface DevelopmentSqlLineagePreviewRequest {
  sql: string;
  dataSourceId?: DevelopmentId;
  databaseName?: string;
  schemaName?: string;
}

export interface DevelopmentSqlLineageAssetView {
  assetKey: string;
  assetType: string;
  name: string;
  dataSourceId?: string | null;
  databaseName?: string | null;
  schemaName?: string | null;
  tableName?: string | null;
  columnName?: string | null;
}

export interface DevelopmentSqlLineageRelationView {
  sourceAssetKey: string;
  targetAssetKey: string;
  relationType: string;
  expression?: string | null;
  confidence?: number | null;
}

export interface DevelopmentSqlLineagePreview {
  supported: boolean;
  message?: string | null;
  assets: DevelopmentSqlLineageAssetView[];
  relations: DevelopmentSqlLineageRelationView[];
}

export interface YakEditorSettings {
  fontSize: number;
  tabSize: number;
  insertSpaces: boolean;
  wordWrap: string;
  minimapEnabled: boolean;
  lineNumbers: string;
}
