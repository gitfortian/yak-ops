package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.Variable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SpiDataSourceCatalogGatewayTest {

  private final DataSourcePluginRegistry registry = mock(DataSourcePluginRegistry.class);
  private final DataSourcePlugin plugin = mock(DataSourcePlugin.class);
  private final DataSourceConnection connection = mock(DataSourceConnection.class);
  private final DataSourceCatalog catalog = mock(DataSourceCatalog.class);
  private final SpiDataSourceCatalogGateway gateway = new SpiDataSourceCatalogGateway(registry);

  @Test
  void listTablesTranslatesPluginMetadataToCatalogDomain() {
    DataSourceDefinition dataSource = configuredDataSource();
    stubCatalog(dataSource);
    when(catalog.listTables(any(DataSourceCatalogQuery.class)))
        .thenReturn(
            List.of(
                new DataSourceTable(
                    "orders_db", "public", "orders", "TABLE", "order table")));

    List<CatalogTable> result =
        gateway.listTables(
            dataSource,
            new CatalogTableQuery("orders_db", "public", "ord"),
            5);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().database()).isEqualTo("orders_db");
    assertThat(result.getFirst().schema()).isEqualTo("public");
    assertThat(result.getFirst().name()).isEqualTo("orders");
    assertThat(result.getFirst().type()).isEqualTo("TABLE");
    assertThat(result.getFirst().remarks()).isEqualTo("order table");
  }

  @Test
  void typedCatalogRequestIsProjectedToLegacyPluginMapOnlyInsideAdapter() {
    DataSourceDefinition dataSource = configuredDataSource();
    stubCatalog(dataSource);
    when(catalog.preview(any(Map.class), eq(20)))
        .thenReturn(new DataSourceQueryResult(List.of(), List.of(), 0L));
    CatalogReadRequest request =
        new CatalogReadRequest(
            ReadMode.SQL,
            null,
            "select * from orders where day = ${day}",
            List.of(new Variable("day", "2026-08-23")));

    CatalogQueryResult result = gateway.preview(dataSource, request, 20, 5);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(catalog).preview(captor.capture(), eq(20));
    Map<String, Object> pluginRequest = captor.getValue();
    assertThat(pluginRequest.get("read_mode")).isEqualTo("sql");
    assertThat(pluginRequest.get("query")).isEqualTo(request.sql());
    assertThat(pluginRequest.get("paramsList")).isInstanceOf(List.class);
    assertThat(result.total()).isZero();
  }

  private void stubCatalog(DataSourceDefinition dataSource) {
    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(plugin.parseConnection(dataSource.getConnectionParams())).thenReturn(connection);
    when(plugin.createCatalog(connection, 5)).thenReturn(catalog);
  }

  private DataSourceDefinition configuredDataSource() {
    DataSourceDefinition dataSource = new DataSourceDefinition();
    dataSource.setDbType(DataSourceDbType.MYSQL);
    dataSource.setConnectionParams("{\"database\":\"orders_db\"}");
    return dataSource;
  }
}
