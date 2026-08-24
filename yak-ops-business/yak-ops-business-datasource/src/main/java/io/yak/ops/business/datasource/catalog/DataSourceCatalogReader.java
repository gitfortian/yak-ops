package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTablePath;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.query.DataSourceReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads datasource catalog metadata and preview data through the typed catalog gateway. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceCatalogReader {

  private static final int PREVIEW_LIMIT = 20;

  private final DataSourceReader dataSourceReader;
  private final DataSourceCatalogGateway catalogGateway;
  private final DataSourceProperties properties;
  private final CatalogReadPolicy readPolicy;
  private final CatalogTableMatcher tableMatcher;

  public List<String> listDatabases(Long dataSourceId) {
    return catalogGateway.listDatabases(
        dataSourceReader.require(dataSourceId),
        connectionTimeoutSeconds());
  }

  public List<String> listSchemas(Long dataSourceId, String database) {
    return catalogGateway.listSchemas(
        dataSourceReader.require(dataSourceId),
        database,
        connectionTimeoutSeconds());
  }

  public List<CatalogTable> listTables(
      Long dataSourceId,
      String database,
      String schema,
      String keyword) {
    return catalogGateway.listTables(
        dataSourceReader.require(dataSourceId),
        new CatalogTableQuery(database, schema, keyword),
        connectionTimeoutSeconds());
  }

  public List<CatalogColumn> listColumns(
      Long dataSourceId,
      String database,
      String schema,
      String table) {
    return catalogGateway.listColumns(
        dataSourceReader.require(dataSourceId),
        new CatalogTablePath(database, schema, table),
        connectionTimeoutSeconds());
  }

  public List<CatalogTable> listTable(Long dataSourceId) {
    return listAllTables(dataSourceId);
  }

  public List<CatalogTable> listTableReference(
      Long dataSourceId,
      String matchMode,
      String keyword) {
    return tableMatcher.match(listAllTables(dataSourceId), matchMode, keyword);
  }

  public List<CatalogColumn> listColumn(
      Long dataSourceId,
      CatalogReadRequest request) {
    readPolicy.validateReadOnly(request);
    return catalogGateway.describe(
        dataSourceReader.require(dataSourceId),
        request,
        connectionTimeoutSeconds());
  }

  public CatalogQueryResult preview(
      Long dataSourceId,
      CatalogReadRequest request) {
    readPolicy.validateReadOnly(request);
    return catalogGateway.preview(
        dataSourceReader.require(dataSourceId),
        request,
        PREVIEW_LIMIT,
        connectionTimeoutSeconds());
  }

  public Long count(
      Long dataSourceId,
      CatalogReadRequest request) {
    readPolicy.validateReadOnly(request);
    return catalogGateway.count(
        dataSourceReader.require(dataSourceId),
        request,
        connectionTimeoutSeconds());
  }

  public String buildSqlTemplate(Long dataSourceId, String tablePath) {
    return catalogGateway.buildSqlTemplate(
        dataSourceReader.require(dataSourceId),
        tablePath,
        connectionTimeoutSeconds());
  }

  public String resolveSql(
      Long dataSourceId,
      CatalogReadRequest request) {
    readPolicy.requireSql(request);
    return catalogGateway.resolveSql(
        dataSourceReader.require(dataSourceId),
        request,
        connectionTimeoutSeconds());
  }

  private List<CatalogTable> listAllTables(Long dataSourceId) {
    DataSourceDefinition definition = dataSourceReader.require(dataSourceId);
    return catalogGateway.listTables(
        definition,
        new CatalogTableQuery(null, null, null),
        connectionTimeoutSeconds());
  }

  private int connectionTimeoutSeconds() {
    return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
  }
}
