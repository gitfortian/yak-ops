import type {
  OfflineConnectorRole,
  OfflineConnectorRoleRuntime,
  OfflineConnectorRuntimeSnapshot,
} from '@/services/batch-link-up';

import type { SyncEditorState } from './model';

export const CONNECTOR_CAPABILITY = {
  TABLE_SCHEMA_DISCOVERY: 'TABLE_SCHEMA_DISCOVERY',
  MULTI_TABLE: 'MULTI_TABLE',
  CUSTOM_SQL: 'CUSTOM_SQL',
  PARTITION_SPLIT: 'PARTITION_SPLIT',
  UPSERT: 'UPSERT',
  AUTO_CREATE_TABLE: 'AUTO_CREATE_TABLE',
  DIRTY_DATA_HANDLING: 'DIRTY_DATA_HANDLING',
  TWO_PHASE_COMMIT: 'TWO_PHASE_COMMIT',
} as const;

export type ConnectorCapability =
  (typeof CONNECTOR_CAPABILITY)[keyof typeof CONNECTOR_CAPABILITY];

export interface EndpointCapabilityState {
  connectorId: string;
  role: OfflineConnectorRole;
  runtimeChecked: boolean;
  workerReachable: boolean;
  stale: boolean;
  metadataKnown: boolean;
  available: boolean;
  capabilities: string[];
  runtime?: OfflineConnectorRoleRuntime;
}

const normalizeConnectorId = (value: string) =>
  String(value || '').trim().toLowerCase();

const normalizeCapability = (value: string) =>
  String(value || '').trim().toUpperCase();

export const resolveEndpointCapability = (
  snapshot: OfflineConnectorRuntimeSnapshot | null | undefined,
  connectorId: string,
  role: OfflineConnectorRole,
): EndpointCapabilityState => {
  const normalizedId = normalizeConnectorId(connectorId);
  const runtime = snapshot?.connectors?.find(
    (item) =>
      normalizeConnectorId(item.connectorId) === normalizedId &&
      item.role === role,
  );
  const capabilities = Array.from(
    new Set(
      (runtime?.capabilities || [])
        .map(normalizeCapability)
        .filter(Boolean),
    ),
  ).sort();

  return {
    connectorId: normalizedId,
    role,
    runtimeChecked: Boolean(snapshot),
    workerReachable: snapshot?.reachable === true,
    stale: snapshot?.stale === true,
    metadataKnown: Boolean(runtime),
    available: Boolean(snapshot?.reachable && runtime?.available),
    capabilities,
    runtime,
  };
};

export const hasCapability = (
  state: EndpointCapabilityState,
  capability: ConnectorCapability | string,
) => state.capabilities.includes(normalizeCapability(capability));

/**
 * UI compatibility rule: before runtime metadata is known, keep the historical controls visible.
 * Once the Worker has described a connector role, the capability list becomes authoritative.
 */
export const allowsCapability = (
  state: EndpointCapabilityState,
  capability: ConnectorCapability | string,
) => !state.metadataKnown || hasCapability(state, capability);

export const isDefinitivelyUnavailable = (
  state: EndpointCapabilityState,
) => state.runtimeChecked && state.workerReachable && !state.available;

export const validateEditorCapabilities = (
  editor: SyncEditorState,
  snapshot: OfflineConnectorRuntimeSnapshot | null | undefined,
): string[] => {
  if (!snapshot || !snapshot.reachable) {
    return [];
  }

  const source = resolveEndpointCapability(
    snapshot,
    editor.source.connectorId,
    'SOURCE',
  );
  const sink = resolveEndpointCapability(
    snapshot,
    editor.sink.connectorId,
    'SINK',
  );
  const errors: string[] = [];

  if (!source.available) {
    errors.push(
      `当前 Link-Up Worker 未加载 Source Connector：${editor.source.connectorId || 'UNKNOWN'}`,
    );
  }
  if (!sink.available) {
    errors.push(
      `当前 Link-Up Worker 未加载 Sink Connector：${editor.sink.connectorId || 'UNKNOWN'}`,
    );
  }
  if (errors.length > 0) {
    return errors;
  }

  if (editor.mode === 'GUIDE_MULTI') {
    if (!hasCapability(source, CONNECTOR_CAPABILITY.MULTI_TABLE)) {
      errors.push(
        `Source Connector ${source.connectorId} 不支持多表同步（MULTI_TABLE）`,
      );
    }
    if (!hasCapability(sink, CONNECTOR_CAPABILITY.MULTI_TABLE)) {
      errors.push(
        `Sink Connector ${sink.connectorId} 不支持多表同步（MULTI_TABLE）`,
      );
    }
  }

  const sourceConfig = editor.source.config || {};
  const sinkConfig = editor.sink.config || {};

  if (
    String(sourceConfig.readMode || '').toLowerCase() === 'sql' &&
    !hasCapability(source, CONNECTOR_CAPABILITY.CUSTOM_SQL)
  ) {
    errors.push(
      `Source Connector ${source.connectorId} 不支持自定义 SQL（CUSTOM_SQL）`,
    );
  }

  if (
    Boolean(sinkConfig.autoCreateTable) &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.AUTO_CREATE_TABLE)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持自动建表（AUTO_CREATE_TABLE）`,
    );
  }

  if (
    String(sinkConfig.writeMode || '').toLowerCase() === 'upsert' &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.UPSERT)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持 Upsert（UPSERT）`,
    );
  }

  if (
    editor.channel.dirtyDataPolicy === 'skip' &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.DIRTY_DATA_HANDLING)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持跳过脏数据（DIRTY_DATA_HANDLING）`,
    );
  }

  return errors;
};
