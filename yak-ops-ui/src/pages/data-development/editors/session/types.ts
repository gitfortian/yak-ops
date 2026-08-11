import type {
  DevelopmentId,
  DevelopmentTaskType,
} from '../../types';

export interface DevelopmentEditorSelection {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
}

export interface DevelopmentEditorViewState {
  lineNumber: number;
  column: number;
  selection?: DevelopmentEditorSelection;
  scrollTop: number;
  scrollLeft: number;
}

export interface DevelopmentEditorSession {
  nodeId: DevelopmentId;
  nodeType: DevelopmentTaskType;
  content: string;
  originalContent: string;
  dirty: boolean;
  viewState?: DevelopmentEditorViewState;
  updatedAt: number;
}
