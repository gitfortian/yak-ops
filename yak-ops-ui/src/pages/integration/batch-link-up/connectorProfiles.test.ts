import {
  connectorIdForDataSourceType,
  connectorIdForNewDataSourceType,
  defaultOfflineSyncConnectorProfile,
  isGuideMultiEnabledForDataSourceType,
  OFFLINE_SYNC_CONNECTOR_PROFILES,
} from './connectorProfiles';

test('uses native profiles for new OLAP and versioned Elasticsearch tasks while keeping JDBC explicit', () => {
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
    'ELASTICSEARCH7',
    'ELASTICSEARCH8',
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
  expect(defaultOfflineSyncConnectorProfile('ELASTICSEARCH7')).toMatchObject({
    profileId: 'elasticsearch7-native',
    sourceConnectorId: 'elasticsearch7',
    sinkConnectorId: 'elasticsearch7',
    guideMultiEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('ELASTICSEARCH8')).toMatchObject({
    profileId: 'elasticsearch8-native',
    sourceConnectorId: 'elasticsearch8',
    sinkConnectorId: 'elasticsearch8',
    guideMultiEnabled: false,
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
  expect(connectorIdForNewDataSourceType('ES7')).toBe('elasticsearch7');
  expect(connectorIdForNewDataSourceType('ELASTICSEARCH_8')).toBe('elasticsearch8');
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
  expect(defaultOfflineSyncConnectorProfile('ES8')).toMatchObject({
    dbType: 'ELASTICSEARCH8',
    sourceConnectorId: 'elasticsearch8',
  });
});

test('keeps Elasticsearch on the single-table guide without hardcoding drawer connector sets', () => {
  expect(isGuideMultiEnabledForDataSourceType('MYSQL')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('DORIS')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('ELASTICSEARCH7')).toBe(false);
  expect(isGuideMultiEnabledForDataSourceType('ES8')).toBe(false);
});
