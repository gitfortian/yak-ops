export type OfflineSyncConnectorRole = 'SOURCE' | 'SINK';

export interface OfflineSyncConnectorProfile {
  profileId: string;
  dbType: string;
  sourceConnectorId: string;
  sinkConnectorId: string;
  /** Historical UI label retained until the old editor is fully removed. */
  connectorType: string;
  pluginName: string;
  defaultProfile: boolean;
}

/**
 * Control-plane default execution profiles for the datasource types Yak Ops exposes today.
 *
 * Keep this module as the only frontend dbType -> connector mapping. The presentation layer may
 * still own icons/labels, but execution identity must not be redefined in individual components.
 */
export const OFFLINE_SYNC_CONNECTOR_PROFILES: readonly OfflineSyncConnectorProfile[] = [
  {
    profileId: 'mysql-jdbc',
    dbType: 'MYSQL',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc',
    pluginName: 'JDBC-MYSQL',
    defaultProfile: true,
  },
  {
    profileId: 'oracle-jdbc',
    dbType: 'ORACLE',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc',
    pluginName: 'JDBC-ORACLE',
    defaultProfile: true,
  },
  {
    profileId: 'postgresql-jdbc',
    dbType: 'POSTGRE_SQL',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc',
    pluginName: 'JDBC-POSTGRESQL',
    defaultProfile: true,
  },
  {
    profileId: 'doris-jdbc',
    dbType: 'DORIS',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Doris',
    pluginName: 'DORIS',
    defaultProfile: true,
  },
  {
    profileId: 'kingbase-jdbc',
    dbType: 'KINGBASE',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc',
    pluginName: 'JDBC-KINGBASE',
    defaultProfile: true,
  },
  {
    profileId: 'dameng-jdbc',
    dbType: 'DAMENG',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc',
    pluginName: 'JDBC-DAMENG',
    defaultProfile: true,
  },
] as const;

const LEGACY_JDBC_TYPES = new Set([
  'JDBC',
  'MARIADB',
  'SQLSERVER',
  'SQL_SERVER',
  'STARROCKS',
  'CLICKHOUSE',
  'DB2',
  'HIVE',
  'DM',
]);

const DB_TYPE_ALIASES: Record<string, string> = {
  POSTGRESQL: 'POSTGRE_SQL',
  POSTGRES: 'POSTGRE_SQL',
};

const normalizeDbType = (value?: string): string => {
  const normalized = String(value || '').trim().toUpperCase().replace(/-/g, '_');
  return DB_TYPE_ALIASES[normalized] || normalized;
};

export const defaultOfflineSyncConnectorProfile = (
  dbType?: string,
): OfflineSyncConnectorProfile | undefined => {
  const normalized = normalizeDbType(dbType);
  return OFFLINE_SYNC_CONNECTOR_PROFILES.find(
    (profile) => profile.defaultProfile && profile.dbType === normalized,
  );
};

export const connectorIdForDataSourceType = (
  value?: string,
  role: OfflineSyncConnectorRole = 'SOURCE',
): string => {
  const normalized = normalizeDbType(value);
  if (!normalized) return '';

  const profile = defaultOfflineSyncConnectorProfile(normalized);
  if (profile) {
    return role === 'SINK' ? profile.sinkConnectorId : profile.sourceConnectorId;
  }
  return LEGACY_JDBC_TYPES.has(normalized)
    ? 'jdbc'
    : normalized.toLowerCase();
};
