export const WORKFLOW_NOTE_NODE_PREFIX = '__yak_note__';
export const WORKFLOW_EDITOR_META_KEY = '__yak_editor__';

export type WorkflowNoteTheme = 'blue' | 'cyan' | 'green' | 'yellow' | 'pink' | 'violet';

export interface WorkflowNoteData {
  text: string;
  html?: string;
  theme: WorkflowNoteTheme;
  locked?: boolean;
  onChange?: (
    nodeId: string,
    patch: Partial<Pick<WorkflowNoteData, 'text' | 'html' | 'theme'>>,
  ) => void;
  onCommit?: (nodeId: string, label: string) => void;
  onDuplicate?: (nodeId: string) => void;
  onDelete?: (nodeId: string) => void;
}

export interface WorkflowNoteSnapshot {
  id: string;
  position: { x: number; y: number };
  width: number;
  height: number;
  text: string;
  html?: string;
  theme: WorkflowNoteTheme;
}
