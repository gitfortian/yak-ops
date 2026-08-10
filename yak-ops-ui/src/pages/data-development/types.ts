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
