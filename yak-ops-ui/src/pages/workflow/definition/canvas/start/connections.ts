import { useSyncExternalStore } from 'react';

let connectionIds = new Set<string>();
let snapshot: string[] = [];
const listeners = new Set<() => void>();

const emit = () => {
  snapshot = [...connectionIds];
  listeners.forEach((listener) => listener());
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const getWorkflowStartConnections = () => snapshot;

export const hasWorkflowStartConnection = (nodeId: string) => connectionIds.has(nodeId);

export const replaceWorkflowStartConnections = (nodeIds: string[]) => {
  connectionIds = new Set(nodeIds.filter(Boolean));
  emit();
};

export const addWorkflowStartConnection = (nodeId: string) => {
  if (!nodeId || connectionIds.has(nodeId)) return;
  connectionIds.add(nodeId);
  emit();
};

export const removeWorkflowStartConnection = (nodeId: string) => {
  if (!connectionIds.delete(nodeId)) return;
  emit();
};

export const useWorkflowStartConnections = () =>
  useSyncExternalStore(subscribe, getWorkflowStartConnections, getWorkflowStartConnections);
