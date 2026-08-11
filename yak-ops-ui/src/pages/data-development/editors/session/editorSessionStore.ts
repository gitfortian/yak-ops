import { useSyncExternalStore } from 'react';

import type {
  DevelopmentId,
  DevelopmentTaskType,
} from '../../types';
import type {
  DevelopmentEditorSession,
  DevelopmentEditorViewState,
} from './types';

const STORAGE_KEY = 'yak-data-development.editor-sessions.v1';
const PERSIST_DELAY = 200;

interface PersistedEditorSessions {
  version: 1;
  sessions: DevelopmentEditorSession[];
}

const sessions = new Map<DevelopmentId, DevelopmentEditorSession>();
const listeners = new Set<() => void>();

let hydrated = false;
let version = 0;
let persistTimer: number | undefined;
let pageHideBound = false;

const isBrowser = () => typeof window !== 'undefined';

const sameViewState = (
  left?: DevelopmentEditorViewState,
  right?: DevelopmentEditorViewState,
) => {
  if (left === right) return true;
  if (!left || !right) return false;

  const leftSelection = left.selection;
  const rightSelection = right.selection;

  return (
    left.lineNumber === right.lineNumber &&
    left.column === right.column &&
    left.scrollTop === right.scrollTop &&
    left.scrollLeft === right.scrollLeft &&
    leftSelection?.startLineNumber === rightSelection?.startLineNumber &&
    leftSelection?.startColumn === rightSelection?.startColumn &&
    leftSelection?.endLineNumber === rightSelection?.endLineNumber &&
    leftSelection?.endColumn === rightSelection?.endColumn
  );
};

const persistNow = () => {
  if (!isBrowser()) return;
  if (persistTimer !== undefined) {
    window.clearTimeout(persistTimer);
    persistTimer = undefined;
  }

  const payload: PersistedEditorSessions = {
    version: 1,
    sessions: [...sessions.values()],
  };

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Local storage can be unavailable or full. The in-memory session still works.
  }
};

const schedulePersist = () => {
  if (!isBrowser()) return;
  if (persistTimer !== undefined) window.clearTimeout(persistTimer);
  persistTimer = window.setTimeout(persistNow, PERSIST_DELAY);
};

const bindPageHide = () => {
  if (!isBrowser() || pageHideBound) return;
  pageHideBound = true;
  window.addEventListener('pagehide', persistNow);
};

const isPersistedSession = (
  value: unknown,
): value is DevelopmentEditorSession => {
  if (!value || typeof value !== 'object') return false;
  const session = value as Partial<DevelopmentEditorSession>;

  return (
    typeof session.nodeId === 'string' &&
    typeof session.nodeType === 'string' &&
    typeof session.content === 'string' &&
    typeof session.originalContent === 'string' &&
    typeof session.dirty === 'boolean' &&
    typeof session.updatedAt === 'number'
  );
};

const ensureHydrated = () => {
  if (hydrated) return;
  hydrated = true;
  bindPageHide();

  if (!isBrowser()) return;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;

    const parsed = JSON.parse(raw) as Partial<PersistedEditorSessions>;
    if (parsed.version !== 1 || !Array.isArray(parsed.sessions)) return;

    parsed.sessions.forEach((session) => {
      if (!isPersistedSession(session)) return;
      sessions.set(session.nodeId, session);
    });
  } catch {
    // Ignore malformed or unavailable persisted state and start with a clean store.
  }
};

const emitChange = () => {
  version += 1;
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  ensureHydrated();
  listeners.add(listener);
  return () => listeners.delete(listener);
};

const getVersion = () => {
  ensureHydrated();
  return version;
};

export const ensureEditorSession = (
  nodeId: DevelopmentId,
  nodeType: DevelopmentTaskType,
  initialContent = '',
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (current) return current;

  const session: DevelopmentEditorSession = {
    nodeId,
    nodeType,
    content: initialContent,
    originalContent: initialContent,
    dirty: false,
    updatedAt: Date.now(),
  };
  sessions.set(nodeId, session);
  return session;
};

export const getEditorSession = (nodeId: DevelopmentId) => {
  ensureHydrated();
  return sessions.get(nodeId);
};

export const updateEditorSessionContent = (
  nodeId: DevelopmentId,
  content: string,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || current.content === content) return;

  sessions.set(nodeId, {
    ...current,
    content,
    dirty: content !== current.originalContent,
    updatedAt: Date.now(),
  });
  schedulePersist();
  emitChange();
};

export const updateEditorSessionViewState = (
  nodeId: DevelopmentId,
  viewState: DevelopmentEditorViewState,
) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || sameViewState(current.viewState, viewState)) return;

  sessions.set(nodeId, {
    ...current,
    viewState,
    updatedAt: Date.now(),
  });
  schedulePersist();
};

export const markEditorSessionSaved = (nodeId: DevelopmentId) => {
  ensureHydrated();
  const current = sessions.get(nodeId);
  if (!current || !current.dirty) return;

  sessions.set(nodeId, {
    ...current,
    originalContent: current.content,
    dirty: false,
    updatedAt: Date.now(),
  });
  schedulePersist();
  emitChange();
};

export const useEditorSession = (
  nodeId: DevelopmentId,
  nodeType: DevelopmentTaskType,
  initialContent = '',
) => {
  useSyncExternalStore(subscribe, getVersion, getVersion);
  return ensureEditorSession(nodeId, nodeType, initialContent);
};

export const useEditorSessionVersion = () =>
  useSyncExternalStore(subscribe, getVersion, getVersion);
