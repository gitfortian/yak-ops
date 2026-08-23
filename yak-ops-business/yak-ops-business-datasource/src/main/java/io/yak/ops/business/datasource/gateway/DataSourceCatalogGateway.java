package io.yak.ops.business.datasource.gateway;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business Datasource 对 Catalog 插件能力的 Port。
 *
 * <p>这里的模型属于 Business Gateway Contract，不暴露 Datasource Plugin SPI Model。Phase 3 会继续把 Catalog
 * 的 Map 协议类型化并评估哪些模型应提升为正式 Catalog Domain Model。
 */
public interface DataSourceCatalogGateway {

  List<String> listDatabases(DataSourceDefinition dataSource, int timeoutSeconds);

  List<String> listSchemas(
      DataSourceDefinition dataSource,
      String database,
      int timeoutSeconds);

  List<Table> listTables(
      DataSourceDefinition dataSource,
      TableQuery query,
      int timeoutSeconds);

  List<Column> listColumns(
      DataSourceDefinition dataSource,
      TablePath tablePath,
      int timeoutSeconds);

  List<Column> describe(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int timeoutSeconds);

  QueryResult preview(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int limit,
      int timeoutSeconds);

  long count(
      DataSourceDefinition dataSource,
      Map<String, Object> request,
      int timeoutSeconds);

  String buildSqlTemplate(
      DataSourceDefinition dataSource,
      String tablePath,
      int timeoutSeconds);

  String resolveSql(
      DataSourceDefinition dataSource,
      String sql,
      Map<String, Object> request,
      int timeoutSeconds);

  record TableQuery(String database, String schema, String keyword) {}

  record TablePath(String database, String schema, String table) {}

  record Table(
      String database,
      String schema,
      String name,
      String type,
      String remarks) {}

  record Column(
      String name,
      String typeName,
      int jdbcType,
      Integer size,
      Integer scale,
      boolean nullable,
      int ordinalPosition,
      boolean primaryKey,
      String remarks) {}

  record QueryColumn(
      String title,
      String dataIndex,
      String key,
      boolean ellipsis) {}

  record QueryResult(
      List<QueryColumn> columns,
      List<Map<String, Object>> data,
      long total) {

    public QueryResult {
      columns = columns == null ? List.of() : List.copyOf(columns);
      if (data == null || data.isEmpty()) {
        data = List.of();
      } else {
        List<Map<String, Object>> copied = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
          copied.add(
              row == null
                  ? Map.of()
                  : Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        data = Collections.unmodifiableList(copied);
      }
    }
  }
}
