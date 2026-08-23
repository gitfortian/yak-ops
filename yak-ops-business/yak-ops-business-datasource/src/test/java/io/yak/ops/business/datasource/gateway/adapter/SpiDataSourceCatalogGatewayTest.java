package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.Table;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.TableQuery;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiDataSourceCatalogGatewayTest {

  private final DataSourcePluginRegistry registry = mock(DataSourcePluginRegistry.class);
  private final DataSourcePlugin plugin = mock(DataSourcePlugin.class);
  private final DataSourceConnection connection = mock(DataSourceConnection.class);
  private final DataSourceCatalog catalog = mock(DataSourceCatalog.class);
  private final SpiDataSourceCatalogGateway gateway =
      new SpiDataSourceCatalogGateway(registry);

  @Test
  void listTablesTranslatesPluginMetadataToBusinessGatewayContract() {
    DataSourceDefinition dataSource = dataSource();
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.parseConnection(dataSource.getConnectionParams())).thenReturn(connection);
    when(plugin.createCatalog(connection, 5)).thenReturn(catalog);
    when(catalog.listTables(any(DataSourceCatalogQuery.class)))
        .thenReturn(
            List.of(
                new DataSourceTable(
                    "orders_db",
                    "public",
                    "orders",
                    "TABLE",
                    "order table")));

    List<Table> result =
        gateway.listTables(
            dataSource,
            new TableQuery("orders_db", "public", "ord"),
            5);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().database()).isEqualTo("orders_db");
    assertThat(result.getFirst().schema()).isEqualTo("public");
    assertThat(result.getFirst().name()).isEqualTo("orders");
    assertThat(result.getFirst().type()).isEqualTo("TABLE");
    assertThat(result.getFirst().remarks()).isEqualTo("order table");
  }

  private DataSourceDefinition dataSource() {
    DataSourceDefinition dataSource = new DataSourceDefinition();
    dataSource.setDbType(DataSourceDbType.MYSQL);
    dataSource.setConnectionParams("{\"database\":\"orders_db\"}");
    return dataSource;
  }
}
