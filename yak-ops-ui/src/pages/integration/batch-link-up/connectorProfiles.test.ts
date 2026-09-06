import {
  connectorIdForDataSourceType,
  connectorIdForNewDataSourceType,
  defaultOfflineSyncConnectorProfile,
  OFFLINE_SYNC_CONNECTOR_PROFILES,
} from './connectorProfiles';

test('uses native profiles for new OLAP tasks while keeping JDBC profiles explicit', () => {
  expect(OFFLINE_SYNC_CONNECTOR_PROFILES.map((profile) => profile.dbType)).toEqual([
    'MYSQL',
    'ORACLE',
    'POSTGRE_SQL',
    'DB2',
    'OPEN_GAUSS',
    'SQL_SERVER',
    'OCEANBASE',
    'DORIS',
    'STARROCKS',
    'CLICKHOUSE',
    'KINGBASE',
    'DAMENG',
  ]);
  expect(defaultOfflineSyncConnectorProfile('DORIS')).toMatchObject({
    profileId: 'doris-native',
    sourceConnectorId: 'doris',
    sinkConnectorId: 'doris',
  });
  expect(defaultOfflineSyncConnectorProfile('STARROCKS')).toMatchObject({
    profileId: 'starrocks-native',
    sourceConnectorId: 'starrocks',
  });
  expect(defaultOfflineSyncConnectorProfile('CLICKHOUSE')).toMatchObject({
    profileId: 'clickhouse-native',
    sourceConnectorId: 'clickhouse',
  });
  expect(defaultOfflineSyncConnectorProfile('DB2')?.sourceConnectorId).toBe('jdbc');
});

test('separates legacy inference from new-task profile selection', () => {
  expect(connectorIdForDataSourceType('DORIS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('STARROCKS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('CLICKHOUSE')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('DORIS')).toBe('doris');
  expect(connectorIdForNewDataSourceType('STARROCKS')).toBe('starrocks');
  expect(connectorIdForNewDataSourceType('CLICKHOUSE')).toBe('clickhouse');
});

test('resolves datasource aliases from one frontend registry', () => {
  expect(connectorIdForDataSourceType('POSTGRESQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('DB2')).toBe('jdbc');
  expect(connectorIdForDataSourceType('opengauss')).toBe('jdbc');
  expect(connectorIdForDataSourceType('SQLSERVER')).toBe('jdbc');
  expect(connectorIdForDataSourceType('MSSQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('OCEANBASE')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HTTP')).toBe('http');
  expect(connectorIdForDataSourceType('custom-api')).toBe('custom_api');
  expect(defaultOfflineSyncConnectorProfile('postgres')).toMatchObject({
    profileId: 'postgresql-jdbc',
    dbType: 'POSTGRE_SQL',
  });
});
