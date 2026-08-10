export type DevelopmentTaskType = 'SQL' | 'SHELL' | 'HTTP' | 'PYTHON';

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
