package io.yak.ops.plugin.database.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.plugin.database.jdbc.dameng.DamengDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.kingbase.KingbaseDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.mysql.MySqlDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.oracle.OracleDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.postgresql.PostgreSqlDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcDatasourcePluginContractTest {

  @Test
  void builtInJdbcPluginsExposeVersionedDescriptorAndRequiredCapabilities() {
    for (DataSourcePlugin plugin : plugins()) {
      DataSourcePluginDescriptor descriptor = plugin.descriptor();

      assertThat(descriptor.dbType()).isEqualTo(plugin.dbType());
      assertThat(descriptor.apiVersion()).isEqualTo(DataSourcePluginDescriptor.CURRENT_API_VERSION);
      assertThat(descriptor.capabilities())
          .contains(
              DataSourceCapability.CONNECTION_TEST,
              DataSourceCapability.CATALOG_METADATA,
              DataSourceCapability.CATALOG_READ,
              DataSourceCapability.SQL_EXECUTION,
              DataSourceCapability.TRANSACTIONS,
              DataSourceCapability.SSH_TUNNEL);
      assertThat(descriptor.secretFieldKeys()).contains("password");
      assertThat(descriptor.connectionForm().allFields())
          .anySatisfy(
              field -> {
                if ("jdbcUrl".equals(field.key())) {
                  assertThat(field.type()).isEqualTo(FieldType.JDBC_URL);
                  assertThat(field.jdbcUrlLinkage()).isNotNull();
                }
              });
    }
  }

  @Test
  void transactionCapabilityNeverExistsWithoutSqlExecution() {
    for (DataSourcePlugin plugin : plugins()) {
      if (plugin.supports(DataSourceCapability.TRANSACTIONS)) {
        assertThat(plugin.supports(DataSourceCapability.SQL_EXECUTION)).isTrue();
      }
    }
  }

  private List<DataSourcePlugin> plugins() {
    return List.of(
        new MySqlDataSourcePlugin(),
        new PostgreSqlDataSourcePlugin(),
        new OracleDataSourcePlugin(),
        new DamengDataSourcePlugin(),
        new KingbaseDataSourcePlugin());
  }
}
