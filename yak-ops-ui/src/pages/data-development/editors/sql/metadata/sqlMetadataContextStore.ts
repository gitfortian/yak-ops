import { useSyncExternalStore } from 'react';

import type { DevelopmentId } from '../../../types';

export interface SqlMetadataContext {
  nodeId: DevelopmentId;
  dataSourceId?: string;
  dataSourceName?: string;
  dbType?: string;
  database?: string;
  schema?: string;
  updatedAt: number;
}

const STORAGE_KEY = 'yak-data-development.sql-metadata-contexts.v1';

interface PersistedSqlMetadataContexts {
  version: 1;
  contexts: SqlMetadataContext[];
}

const contexts = new Map<DevelopmentId, SqlMetadataContext>();
const listeners = new Set<() => void>();

let hydrated = false;
let version = 0;

const isBrowser = () => typeof window !== 'undefined';

const isPersistedContext = (value: unknown): value is SqlMetadataContext => {
  if (!value || typeof value !== 'object') return false;
  const context = value as Partial<SqlMetadataContext>;
  return (
    typeof context.nodeId === 'string' &&
    typeof context.updatedAt === 'number' &&
    (context.dataSourceId === undefined || typeof context.dataSourceId === 'string') &&
    (context.dataSourceName === undefined || typeof context.dataSourceName === 'string') &&
    (context.dbType === undefined || typeof context.dbType === 'string') &&
    (context.database === undefined || typeof context.database === 'string') &&
    (context.schema === undefined || typeof context.schema === 'string')
  );
};

const ensureHydrated = () => {
  if (hydrated) return;
  hydrated = true;
  if (!isBrowser()) return;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as Partial<PersistedSqlMetadataContexts>;
    if (parsed.version !== 1 || !Array.isArray(parsed.contexts)) return;
    parsed.contexts.forEach((context) => {
      if (isPersistedContext(context)) contexts.set(context.nodeId, context);
    });
  } catch {
    // Ignore malformed or unavailable local storage. SQL editing still works in memory.
  }
};

const persist = () => {
  if (!isBrowser()) return;
  const payload: PersistedSqlMetadataContexts = {
    version: 1,
    contexts: [...contexts.values()],
  };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Keep the in-memory context when local storage is unavailable or full.
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

export const ensureSqlMetadataContext = (
  nodeId: DevelopmentId,
): SqlMetadataContext => {
  ensureHydrated();
  const current = contexts.get(nodeId);
  if (current) return current;

  const context: SqlMetadataContext = {
    nodeId,
    updatedAt: Date.now(),
  };
  contexts.set(nodeId, context);
  return context;
};

export const getSqlMetadataContext = (nodeId: DevelopmentId) => {
  ensureHydrated();
  return contexts.get(nodeId);
};

export const updateSqlMetadataContext = (
  nodeId: DevelopmentId,
  patch: Partial<Omit<SqlMetadataContext, 'nodeId' | 'updatedAt'>>,
) => {
  const current = ensureSqlMetadataContext(nodeId);
  const next: SqlMetadataContext = {
    ...current,
    ...patch,
    nodeId,
    updatedAt: Date.now(),
  };
  contexts.set(nodeId, next);
  persist();
  emitChange();
  return next;
};

export const selectSqlDataSourceContext = (
  nodeId: DevelopmentId,
  dataSource?: { id: string; name?: string; dbType?: string },
) =>
  updateSqlMetadataContext(nodeId, {
    dataSourceId: dataSource?.id,
    dataSourceName: dataSource?.name,
    dbType: dataSource?.dbType,
    database: undefined,
    schema: undefined,
  });

export const selectSqlDatabaseContext = (
  nodeId: DevelopmentId,
  database?: string,
) =>
  updateSqlMetadataContext(nodeId, {
    database,
    schema: undefined,
  });

export const selectSqlSchemaContext = (
  nodeId: DevelopmentId,
  schema?: string,
) => updateSqlMetadataContext(nodeId, { schema });

export const useSqlMetadataContext = (nodeId: DevelopmentId) => {
  useSyncExternalStore(subscribe, getVersion, getVersion);
  return ensureSqlMetadataContext(nodeId);
};
