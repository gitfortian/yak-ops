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
  /** Product-level guide policy. Backend adapters remain the final enforcement boundary. */
  guideMultiEnabled?: boolean;
}

/** Control-plane default execution profiles for datasource types exposed by Yak Ops. */
export const OFFLINE_SYNC_CONNECTOR_PROFILES: readonly OfflineSyncConnectorProfile[] = [
  {
    profileId: 'mysql-jdbc', dbType: 'MYSQL', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-MYSQL', defaultProfile: true,
  },
  {
    profileId: 'oracle-jdbc', dbType: 'ORACLE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-ORACLE', defaultProfile: true,
  },
  {
    profileId: 'postgresql-jdbc', dbType: 'POSTGRE_SQL', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-POSTGRESQL', defaultProfile: true,
  },
  {
    profileId: 'db2-jdbc', dbType: 'DB2', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-DB2', defaultProfile: true,
  },
  {
    profileId: 'opengauss-jdbc', dbType: 'OPEN_GAUSS', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-OPENGAUSS', defaultProfile: true,
  },
  {
    profileId: 'sqlserver-jdbc', dbType: 'SQL_SERVER', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-SQLSERVER', defaultProfile: true,
  },
  {
    profileId: 'oceanbase-jdbc', dbType: 'OCEANBASE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-OCEANBASE', defaultProfile: true,
  },
  {
    profileId: 'doris-native', dbType: 'DORIS', sourceConnectorId: 'doris', sinkConnectorId: 'doris',
    connectorType: 'Doris', pluginName: 'DORIS', defaultProfile: true,
  },
  {
    profileId: 'starrocks-native', dbType: 'STARROCKS', sourceConnectorId: 'starrocks', sinkConnectorId: 'starrocks',
    connectorType: 'StarRocks', pluginName: 'STARROCKS', defaultProfile: true,
  },
  {
    profileId: 'clickhouse-native', dbType: 'CLICKHOUSE', sourceConnectorId: 'clickhouse', sinkConnectorId: 'clickhouse',
    connectorType: 'ClickHouse', pluginName: 'CLICKHOUSE', defaultProfile: true,
  },
  {
    profileId: 'elasticsearch7-native', dbType: 'ELASTICSEARCH7',
    sourceConnectorId: 'elasticsearch7', sinkConnectorId: 'elasticsearch7',
    connectorType: 'Elasticsearch7', pluginName: 'ELASTICSEARCH7', defaultProfile: true,
    guideMultiEnabled: false,
  },
  {
    profileId: 'elasticsearch8-native', dbType: 'ELASTICSEARCH8',
    sourceConnectorId: 'elasticsearch8', sinkConnectorId: 'elasticsearch8',
    connectorType: 'Elasticsearch8', pluginName: 'ELASTICSEARCH8', defaultProfile: true,
    guideMultiEnabled: false,
  },
  {
    profileId: 'kingbase-jdbc', dbType: 'KINGBASE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-KINGBASE', defaultProfile: true,
  },
  {
    profileId: 'dameng-jdbc', dbType: 'DAMENG', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-DAMENG', defaultProfile: true,
  },
] as const;

/**
 * Records without connectorId are legacy definitions. Keep their historical JDBC inference so
 * merely opening/saving an old task cannot silently switch its engine implementation.
 */
const LEGACY_JDBC_TYPES = new Set([
  'JDBC', 'MARIADB', 'DORIS', 'STARROCKS', 'CLICKHOUSE', 'HIVE', 'DM',
]);

const DB_TYPE_ALIASES: Record<string, string> = {
  POSTGRESQL: 'POSTGRE_SQL',
  POSTGRES: 'POSTGRE_SQL',
  OPENGAUSS: 'OPEN_GAUSS',
  SQLSERVER: 'SQL_SERVER',
  MSSQL: 'SQL_SERVER',
  ELASTICSEARCH_7: 'ELASTICSEARCH7',
  ES7: 'ELASTICSEARCH7',
  ELASTICSEARCH_8: 'ELASTICSEARCH8',
  ES8: 'ELASTICSEARCH8',
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

export const isGuideMultiEnabledForDataSourceType = (dbType?: string): boolean =>
  defaultOfflineSyncConnectorProfile(dbType)?.guideMultiEnabled !== false;

/** Compatibility inference used only when a persisted endpoint has no durable connectorId. */
export const connectorIdForDataSourceType = (
  value?: string,
  role: OfflineSyncConnectorRole = 'SOURCE',
): string => {
  const normalized = normalizeDbType(value);
  if (!normalized) return '';
  if (LEGACY_JDBC_TYPES.has(normalized)) return 'jdbc';

  const profile = defaultOfflineSyncConnectorProfile(normalized);
  if (profile) {
    return role === 'SINK' ? profile.sinkConnectorId : profile.sourceConnectorId;
  }
  return normalized.toLowerCase();
};

/** New-task resolver: always uses the current default profile and persists its connectorId. */
export const connectorIdForNewDataSourceType = (
  value?: string,
  role: OfflineSyncConnectorRole = 'SOURCE',
): string => {
  const normalized = normalizeDbType(value);
  if (!normalized) return '';
  const profile = defaultOfflineSyncConnectorProfile(normalized);
  if (profile) {
    return role === 'SINK' ? profile.sinkConnectorId : profile.sourceConnectorId;
  }
  return normalized.toLowerCase();
};
