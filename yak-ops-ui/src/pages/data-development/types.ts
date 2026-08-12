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
