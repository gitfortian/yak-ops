package io.yak.ops.business.datasource.gateway.adapter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryColumn;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Datasource Catalog SPI -> Business Gateway Adapter。 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SpiDataSourceCatalogGateway implements DataSourceCatalogGateway {

  private final DataSourcePluginRegistry pluginRegistry;

  @Override
  public List<String> listDatabases(
      DataSourceDefinition dataSource,
      int timeoutSeconds) {
    return execute(dataSource, timeoutSeconds, DataSourceCatalog::listDatabases);
  }

  @Override
  public List<String> listSchemas(
      DataSourceDefinition dataSource,
      String database,
      int timeoutSeconds) {
    return execute(dataSource, timeoutSeconds, catalog -> catalog.listSchemas(database));
  }

  @Override
  public List<Table> listTables(
      DataSourceDefinition dataSource,
      TableQuery query,
      int timeoutSeconds) {
    TableQuery value = query == null ? new TableQuery(null, null, null) : query;
    return execute(
        dataSource,
        timeoutSeconds,
        catalog ->
            catalog
                .listTables(
                    new DataSourceCatalogQuery(
                        value.database(), value.schema(), value.keyword()))
                .stream()
                .map(this::toTable)
                .toList());
  }

  @Override
  public List<Column> listColumns(
      DataSourceDefinition dataSource,
      TablePath tablePath,
      int timeoutSeconds) {
    if (tablePath == null) {
      throw new DataSourceException(
          DataSourceErrorCode.CATALOG_FAILED,
          "Catalog 表路径不能为空");
    }
    return execute(
        dataSource,
        timeoutSeconds,
        catalog ->
            catalog
                .listColumns(
                    new DataSourceTablePath(
                        tablePath.database(), tablePath.schema(), tablePath.table()))
                .stream()
                .map(this::toColumn)
                .toList());
  }

  @Override
  public List<Column> describe(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int timeoutSeconds) {
    return execute(
        dataSource,
        timeoutSeconds,
        catalog -> catalog.describe(request).stream().map(this::toColumn).toList());
  }

  @Override
  public QueryResult preview(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int limit,
      int timeoutSeconds) {
    return execute(
        dataSource,
        timeoutSeconds,
        catalog -> toQueryResult(catalog.preview(request, limit)));
  }

  @Override
  public long count(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int timeoutSeconds) {
    return execute(dataSource, timeoutSeconds, catalog -> catalog.count(request));
  }

  @Override
  public String buildSqlTemplate(
      DataSourceDefinition dataSource,
      String tablePath,
      int timeoutSeconds) {
    return execute(
        dataSource,
        timeoutSeconds,
        catalog -> catalog.buildSqlTemplate(tablePath));
  }

  @Override
  public String resolveSql(
      DataSourceDefinition dataSource,
      String sql,
      Map<String, Object> request,
      int timeoutSeconds) {
    return execute(
        dataSource,
        timeoutSeconds,
        catalog -> catalog.resolveSql(sql, request));
  }

  private <T> T execute(
      DataSourceDefinition dataSource,
      int timeoutSeconds,
      Function<DataSourceCatalog, T> action) {
    if (dataSource == null || dataSource.getDbType() == null) {
      throw new DataSourceException(
          DataSourceErrorCode.CATALOG_FAILED,
          "数据源定义或类型不能为空");
    }
    try {
      DataSourcePlugin plugin = pluginRegistry.get(dataSource.getDbType());
      DataSourceConnection connection =
          plugin.parseConnection(dataSource.getConnectionParams());
      DataSourceCatalog catalog =
          plugin.createCatalog(connection, Math.max(1, timeoutSeconds));
      return action.apply(catalog);
    } catch (DataSourceException exception) {
      throw exception;
    } catch (DataSourcePluginException exception) {
      throw catalogException(exception);
    } catch (RuntimeException exception) {
      throw catalogException(exception);
    }
  }

  private Table toTable(DataSourceTable table) {
    return new Table(
        table.getDatabase(),
        table.getSchema(),
        table.getName(),
        table.getType(),
        table.getRemarks());
  }

  private Column toColumn(DataSourceColumn column) {
    return new Column(
        column.getName(),
        column.getTypeName(),
        column.getJdbcType(),
        column.getSize(),
        column.getScale(),
        column.isNullable(),
        column.getOrdinalPosition(),
        column.isPrimaryKey(),
        column.getRemarks());
  }

  private QueryResult toQueryResult(DataSourceQueryResult result) {
    if (result == null) {
      return new QueryResult(List.of(), List.of(), 0L);
    }
    List<QueryColumn> columns =
        result.getColumns().stream().map(this::toQueryColumn).toList();
    return new QueryResult(columns, result.getData(), result.getTotal());
  }

  private QueryColumn toQueryColumn(DataSourceQueryColumn column) {
    return new QueryColumn(
        column.getTitle(),
        column.getDataIndex(),
        column.getKey(),
        column.isEllipsis());
  }

  private DataSourceException catalogException(RuntimeException exception) {
    return new DataSourceException(
        DataSourceErrorCode.CATALOG_FAILED,
        exception.getMessage(),
        exception);
  }
}
