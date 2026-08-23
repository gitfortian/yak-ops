package io.yak.ops.business.datasource.service.impl;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.Column;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.QueryResult;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.Table;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.TablePath;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway.TableQuery;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.datasource.service.DataSourceCatalogService;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceQueryResultVO;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Catalog 应用服务；Catalog 物理访问和 SPI 模型转换统一由 Gateway Adapter 负责。 */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {

  private static final int PREVIEW_LIMIT = 20;
  private static final int MAX_MATCH_KEYWORD_LENGTH = 256;
  private static final Pattern READ_ONLY_SELECT = Pattern.compile("(?is)^SELECT\\b.*");

  private final DataSourceRepository repository;
  private final DataSourceCatalogGateway catalogGateway;
  private final DataSourceProperties properties;

  @Override
  public List<String> listDatabases(Long dataSourceId) {
    return catalogGateway.listDatabases(
        getDataSourceOrThrow(dataSourceId),
        connectionTimeoutSeconds());
  }

  @Override
  public List<String> listSchemas(Long dataSourceId, String database) {
    return catalogGateway.listSchemas(
        getDataSourceOrThrow(dataSourceId),
        database,
        connectionTimeoutSeconds());
  }

  @Override
  public List<DataSourceCatalogTableVO> listTables(
      Long dataSourceId,
      String database,
      String schema,
      String keyword) {
    return catalogGateway
        .listTables(
            getDataSourceOrThrow(dataSourceId),
            new TableQuery(database, schema, keyword),
            connectionTimeoutSeconds())
        .stream()
        .map(this::toTableVO)
        .toList();
  }

  @Override
  public List<DataSourceCatalogColumnVO> listColumns(
      Long dataSourceId,
      String database,
      String schema,
      String table) {
    return catalogGateway
        .listColumns(
            getDataSourceOrThrow(dataSourceId),
            new TablePath(database, schema, table),
            connectionTimeoutSeconds())
        .stream()
        .map(this::toColumnVO)
        .toList();
  }

  @Override
  public List<DataSourceCatalogOptionVO> listTable(Long dataSourceId) {
    return catalogGateway
        .listTables(
            getDataSourceOrThrow(dataSourceId),
            new TableQuery(null, null, null),
            connectionTimeoutSeconds())
        .stream()
        .map(
            table -> {
              String label = isBlank(table.remarks()) ? table.name() : table.remarks();
              return new DataSourceCatalogOptionVO(
                  table.name(), label, table.remarks());
            })
        .toList();
  }

  @Override
  public List<DataSourceCatalogOptionVO> listTableReference(
      Long dataSourceId,
      String matchMode,
      String keyword) {
    List<DataSourceCatalogOptionVO> options = listTable(dataSourceId);
    if (isBlank(keyword)) return options;
    if (keyword.length() > MAX_MATCH_KEYWORD_LENGTH) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "表名匹配条件不能超过 " + MAX_MATCH_KEYWORD_LENGTH + " 个字符");
    }

    if ("2".equals(matchMode)) {
      try {
        Pattern pattern = Pattern.compile(keyword);
        return options.stream()
            .filter(option -> pattern.matcher(String.valueOf(option.getValue())).matches())
            .collect(Collectors.toList());
      } catch (PatternSyntaxException exception) {
        throw new DataSourceException(
            DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
            "表名正则表达式不合法：" + exception.getDescription(),
            exception);
      }
    }

    if ("3".equals(matchMode)) {
      Set<String> exactNames =
          Arrays.stream(keyword.split(","))
              .map(String::trim)
              .filter(name -> !name.isEmpty())
              .collect(Collectors.toSet());
      return options.stream()
          .filter(option -> exactNames.contains(String.valueOf(option.getValue())))
          .collect(Collectors.toList());
    }

    return options;
  }

  @Override
  public List<DataSourceCatalogColumnOptionVO> listColumn(
      Long dataSourceId,
      Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    validateReadOnlyRequest(request);
    return catalogGateway
        .describe(
            getDataSourceOrThrow(dataSourceId),
            request,
            connectionTimeoutSeconds())
        .stream()
        .map(this::toColumnOptionVO)
        .toList();
  }

  @Override
  public DataSourceQueryResultVO preview(
      Long dataSourceId,
      Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    validateReadOnlyRequest(request);
    QueryResult result =
        catalogGateway.preview(
            getDataSourceOrThrow(dataSourceId),
            request,
            PREVIEW_LIMIT,
            connectionTimeoutSeconds());
    return toPreviewVO(result);
  }

  @Override
  public Long count(Long dataSourceId, Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    validateReadOnlyRequest(request);
    return catalogGateway.count(
        getDataSourceOrThrow(dataSourceId),
        request,
        connectionTimeoutSeconds());
  }

  @Override
  public String buildSqlTemplate(Long dataSourceId, Map<String, Object> requestBody) {
    String tablePath = requiredText(requireRequest(requestBody), "table_path", "tablePath", "table");
    return catalogGateway.buildSqlTemplate(
        getDataSourceOrThrow(dataSourceId),
        tablePath,
        connectionTimeoutSeconds());
  }

  @Override
  public String resolveSql(Long dataSourceId, Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    String query = requiredText(request, "query", "sql");
    return catalogGateway.resolveSql(
        getDataSourceOrThrow(dataSourceId),
        query,
        request,
        connectionTimeoutSeconds());
  }

  private DataSourceCatalogTableVO toTableVO(Table table) {
    return new DataSourceCatalogTableVO(
        table.database(),
        table.schema(),
        table.name(),
        table.type(),
        table.remarks());
  }

  private DataSourceCatalogColumnVO toColumnVO(Column column) {
    return new DataSourceCatalogColumnVO(
        column.name(),
        column.typeName(),
        column.jdbcType(),
        column.size(),
        column.scale(),
        column.nullable(),
        column.ordinalPosition(),
        column.primaryKey(),
        column.remarks());
  }

  private DataSourceCatalogColumnOptionVO toColumnOptionVO(Column column) {
    return new DataSourceCatalogColumnOptionVO(
        column.ordinalPosition(),
        column.name(),
        column.typeName(),
        column.ordinalPosition(),
        column.nullable() ? "YES" : "NO",
        column.remarks(),
        column.primaryKey() ? "PRI" : "");
  }

  private DataSourceQueryResultVO toPreviewVO(QueryResult result) {
    List<DataSourcePreviewColumnVO> columns =
        result.columns().stream()
            .map(
                column ->
                    new DataSourcePreviewColumnVO(
                        column.title(),
                        column.dataIndex(),
                        column.key(),
                        column.ellipsis()))
            .toList();
    return new DataSourceQueryResultVO(columns, result.data(), result.total());
  }

  private int connectionTimeoutSeconds() {
    return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
  }

  private DataSourceDefinition getDataSourceOrThrow(Long id) {
    if (id == null || id <= 0L) {
      throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
    }
    return repository.findById(id)
        .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
  }

  private Map<String, Object> requireRequest(Map<String, Object> requestBody) {
    if (requestBody == null || requestBody.isEmpty()) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "requestBody 不能为空");
    }
    return requestBody;
  }

  private void validateReadOnlyRequest(Map<String, Object> request) {
    String readMode = optionalText(request, "read_mode", "readMode");
    String tablePath = optionalText(request, "table_path", "tablePath", "table");
    String query = optionalText(request, "query", "sql");
    boolean sqlMode =
        "sql".equalsIgnoreCase(readMode) || (!isBlank(query) && isBlank(tablePath));
    if (!sqlMode) return;
    if (isBlank(query)) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "SQL 模式下 query 不能为空");
    }

    String normalized = query.trim();
    if (normalized.endsWith(";")) {
      normalized = normalized.substring(0, normalized.length() - 1).trim();
    }
    if (normalized.indexOf(';') >= 0 || !READ_ONLY_SELECT.matcher(normalized).matches()) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据预览仅允许执行单条 SELECT 查询");
    }
  }

  private String requiredText(Map<String, Object> request, String... keys) {
    String value = optionalText(request, keys);
    if (!isBlank(value)) return value;
    throw new DataSourceException(
        DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
        keys[0] + " 不能为空");
  }

  private String optionalText(Map<String, Object> request, String... keys) {
    for (String key : keys) {
      Object value = request.get(key);
      if (value != null && !String.valueOf(value).trim().isEmpty()) {
        return String.valueOf(value).trim();
      }
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
