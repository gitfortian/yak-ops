import {
  connectorIdForDataSourceType,
  defaultOfflineSyncConnectorProfile,
  OFFLINE_SYNC_CONNECTOR_PROFILES,
} from './connectorProfiles';

test('keeps first-class datasource profiles on their explicit jdbc execution path', () => {
  expect(OFFLINE_SYNC_CONNECTOR_PROFILES.map((profile) => profile.dbType)).toEqual([
    'MYSQL',
    'ORACLE',
    'POSTGRE_SQL',
    'DB2',
    'OPEN_GAUSS',
    'SQL_SERVER',
    'OCEANBASE',
    'DORIS',
    'KINGBASE',
    'DAMENG',
  ]);
  expect(
    OFFLINE_SYNC_CONNECTOR_PROFILES.every(
      (profile) =>
        profile.sourceConnectorId === 'jdbc' && profile.sinkConnectorId === 'jdbc',
    ),
  ).toBe(true);
});

test('resolves datasource aliases from one frontend registry', () => {
  expect(connectorIdForDataSourceType('POSTGRESQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('DB2')).toBe('jdbc');
  expect(connectorIdForDataSourceType('opengauss')).toBe('jdbc');
  expect(connectorIdForDataSourceType('SQLSERVER')).toBe('jdbc');
  expect(connectorIdForDataSourceType('MSSQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('OCEANBASE')).toBe('jdbc');
  expect(connectorIdForDataSourceType('STARROCKS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HTTP')).toBe('http');
  expect(connectorIdForDataSourceType('custom-api')).toBe('custom_api');
  expect(defaultOfflineSyncConnectorProfile('postgres')).toMatchObject({
    profileId: 'postgresql-jdbc',
    dbType: 'POSTGRE_SQL',
  });
  expect(defaultOfflineSyncConnectorProfile('opengauss')).toMatchObject({
    profileId: 'opengauss-jdbc',
    dbType: 'OPEN_GAUSS',
  });
});
