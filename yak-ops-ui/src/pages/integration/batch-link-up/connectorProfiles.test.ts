import {
  connectorIdForDataSourceType,
  defaultOfflineSyncConnectorProfile,
  OFFLINE_SYNC_CONNECTOR_PROFILES,
} from './connectorProfiles';

test('keeps current datasource profiles on their historical jdbc execution path', () => {
  expect(OFFLINE_SYNC_CONNECTOR_PROFILES.map((profile) => profile.dbType)).toEqual([
    'MYSQL',
    'ORACLE',
    'POSTGRE_SQL',
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

test('resolves aliases from one frontend registry instead of component-local sets', () => {
  expect(connectorIdForDataSourceType('POSTGRESQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('DB2')).toBe('jdbc');
  expect(connectorIdForDataSourceType('STARROCKS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HTTP')).toBe('http');
  expect(connectorIdForDataSourceType('custom-api')).toBe('custom_api');
  expect(defaultOfflineSyncConnectorProfile('postgres')).toMatchObject({
    profileId: 'postgresql-jdbc',
    dbType: 'POSTGRE_SQL',
  });
});
