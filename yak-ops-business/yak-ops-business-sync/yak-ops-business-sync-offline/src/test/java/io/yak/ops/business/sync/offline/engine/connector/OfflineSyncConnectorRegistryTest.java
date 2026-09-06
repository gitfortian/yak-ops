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
    assertThat(profiles.values())
        .allSatisfy(profile -> {
          assertThat(profile.sourceConnectorId()).isEqualTo("jdbc");
          assertThat(profile.sinkConnectorId()).isEqualTo("jdbc");
        });
    assertThat(profiles.get(DataSourceDbType.DB2).pluginName()).isEqualTo("JDBC-DB2");
    assertThat(profiles.get(DataSourceDbType.OPEN_GAUSS).pluginName())
        .isEqualTo("JDBC-OPENGAUSS");
    assertThat(profiles.get(DataSourceDbType.SQL_SERVER).pluginName())
        .isEqualTo("JDBC-SQLSERVER");
    assertThat(profiles.get(DataSourceDbType.OCEANBASE).pluginName())
        .isEqualTo("JDBC-OCEANBASE");
  }

  @Test
  void shouldKeepHistoricalAndProductAliasesOnJdbc() {
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("POSTGRESQL")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("DB2")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("OPENGAUSS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("SQLSERVER")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("OCEANBASE")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("STARROCKS")).contains("jdbc");
    assertThat(OfflineSyncConnectorRegistry.defaultConnectorId("HTTP")).isEmpty();
  }
}
