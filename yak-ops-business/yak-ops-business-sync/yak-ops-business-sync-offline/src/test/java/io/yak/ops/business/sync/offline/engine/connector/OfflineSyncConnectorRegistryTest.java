package io.yak.ops.business.sync.offline.engine.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OfflineSyncConnectorRegistryTest {

  @Test
  void shouldExposeOneDefaultProfileForEveryCurrentDatasourceType() {
    Map<DataSourceDbType, OfflineSyncConnectorProfile> profiles =
        OfflineSyncConnectorRegistry.profiles().stream()
            .filter(OfflineSyncConnectorProfile::defaultProfile)
            .collect(Collectors.toMap(OfflineSyncConnectorProfile::dbType, value -> value));

    assertThat(profiles).containsOnlyKeys(DataSourceDbType.values());
    assertThat(profiles.get(DataSourceDbType.DORIS).sourceConnectorId()).isEqualTo("doris");
    assertThat(profiles.get(DataSourceDbType.STARROCKS).sourceConnectorId()).isEqualTo("starrocks");
    assertThat(profiles.get(DataSourceDbType.CLICKHOUSE).sourceConnectorId()).isEqualTo("clickhouse");
    assertThat(profiles.get(DataSourceDbType.DB2).sourceConnectorId()).isEqualTo("jdbc");
    assertThat(profiles.get(DataSourceDbType.OPEN_GAUSS).sourceConnectorId()).isEqualTo("jdbc");
    assertThat(profiles.get(DataSourceDbType.SQL_SERVER).sourceConnectorId()).isEqualTo("jdbc");
    assertThat(profiles.get(DataSourceDbType.OCEANBASE).sourceConnectorId()).isEqualTo("jdbc");
  }

  @Test
  void shouldKeepDefinitionsWithoutDurableConnectorIdOnHistoricalJdbcPath() {
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("DORIS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("STARROCKS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("CLICKHOUSE")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("POSTGRESQL")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("HTTP")).isEmpty();
  }
}
