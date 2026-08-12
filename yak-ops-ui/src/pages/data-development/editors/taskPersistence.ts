import type {
  DevelopmentNode,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
} from '../types';
import {
  ensureEditorSession,
  getEditorSession,
  hydrateEditorSession,
  restoreEditorSessionOriginal,
  updateEditorSessionConfig,
} from './session/editorSessionStore';
import {
  getSqlTaskConfigJson,
  hydrateSqlTaskConfig,
} from './sql/metadata/sqlMetadataContextStore';

/** Maps editor-local state into the plugin-neutral TaskDefinition envelope. */
export const prepareDevelopmentTaskDefinition = (
  node: DevelopmentNode,
): DevelopmentTaskDefinition => {
  ensureEditorSession(node.id, node.type);

  if (node.type === 'SQL') {
    updateEditorSessionConfig(node.id, getSqlTaskConfigJson(node.id));
  }

  const session = getEditorSession(node.id) || ensureEditorSession(node.id, node.type);
  return {
    taskType: node.type,
    schemaVersion: session.schemaVersion || 1,
    content: session.content,
    configJson: session.configJson || '{}',
  };
};

/** Hydrates a server draft unless a local unsaved session must be preserved. */
export const hydrateDevelopmentTaskDraft = (
  node: DevelopmentNode,
  draft: DevelopmentTaskDraft,
) => {
  const current = getEditorSession(node.id);
  if (current?.dirty) return false;

  hydrateEditorSession(
    node.id,
    node.type,
    draft.definition.schemaVersion,
    draft.definition.content || '',
    draft.definition.configJson || '{}',
    draft.draftRevision,
  );

  if (node.type === 'SQL') {
    hydrateSqlTaskConfig(node.id, draft.definition.configJson || '{}');
  }
  return true;
};

export const restoreDevelopmentTaskOriginal = (node: DevelopmentNode) => {
  const restored = restoreEditorSessionOriginal(node.id);
  if (node.type === 'SQL' && restored) {
    hydrateSqlTaskConfig(node.id, restored.originalConfigJson || '{}');
  }
};
