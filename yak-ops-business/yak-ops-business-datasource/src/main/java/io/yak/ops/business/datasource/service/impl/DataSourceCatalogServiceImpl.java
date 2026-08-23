package io.yak.ops.business.datasource.service.impl;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.Variable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.datasource.domain.catalog.CatalogTablePath;
import io.yak.ops.business.datasource.domain.catalog.CatalogTableQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.datasource.service.DataSourceCatalogService;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceQueryResultVO;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Catalog 应用服务；HTTP Map 兼容协议在入口解析一次，内部统一使用 typed Catalog Domain。 */
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
            new CatalogTableQuery(database, schema, keyword),
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
            new CatalogTablePath(database, schema, table),
            connectionTimeoutSeconds())
        .stream()
        .map(this::toColumnVO)
        .toList();
  }

  @Override
  public List<DataSourceCatalogOptionVO> listTable(Long dataSourceId) {
    return listAllTables(dataSourceId).stream().map(this::toOptionVO).toList();
  }

  @Override
  public List<DataSourceCatalogOptionVO> listTableReference(
      Long dataSourceId,
      String matchMode,
      String keyword) {
    List<CatalogTable> tables = listAllTables(dataSourceId);
    if (isBlank(keyword)) return tables.stream().map(this::toOptionVO).toList();
    if (keyword.length() > MAX_MATCH_KEYWORD_LENGTH) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "表名匹配条件不能超过 " + MAX_MATCH_KEYWORD_LENGTH + " 个字符");
    }

    if ("2".equals(matchMode)) {
      try {
        Pattern pattern = Pattern.compile(keyword);
        return tables.stream()
            .filter(table -> pattern.matcher(table.name()).matches())
            .map(this::toOptionVO)
            .toList();
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
      return tables.stream()
          .filter(table -> exactNames.contains(table.name()))
          .map(this::toOptionVO)
          .toList();
    }

    return tables.stream().map(this::toOptionVO).toList();
  }

  @Override
  public List<DataSourceCatalogColumnOptionVO> listColumn(
      Long dataSourceId,
      Map<String, Object> requestBody) {
    CatalogReadRequest request = toCatalogReadRequest(requestBody);
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
    CatalogReadRequest request = toCatalogReadRequest(requestBody);
    validateReadOnlyRequest(request);
    CatalogQueryResult result =
        catalogGateway.preview(
            getDataSourceOrThrow(dataSourceId),
            request,
            PREVIEW_LIMIT,
            connectionTimeoutSeconds());
    return toPreviewVO(result);
  }

  @Override
  public Long count(Long dataSourceId, Map<String, Object> requestBody) {
    CatalogReadRequest request = toCatalogReadRequest(requestBody);
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
    CatalogReadRequest request = toCatalogReadRequest(requestBody);
    if (request.sql() == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "query 不能为空");
    }
    return catalogGateway.resolveSql(
        getDataSourceOrThrow(dataSourceId),
        request,
        connectionTimeoutSeconds());
  }

  private List<CatalogTable> listAllTables(Long dataSourceId) {
    return catalogGateway.listTables(
        getDataSourceOrThrow(dataSourceId),
        new CatalogTableQuery(null, null, null),
        connectionTimeoutSeconds());
  }

  private DataSourceCatalogTableVO toTableVO(CatalogTable table) {
    return new DataSourceCatalogTableVO(
        table.database(),
        table.schema(),
        table.name(),
        table.type(),
        table.remarks());
  }

  private DataSourceCatalogColumnVO toColumnVO(CatalogColumn column) {
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

  private DataSourceCatalogColumnOptionVO toColumnOptionVO(CatalogColumn column) {
    return new DataSourceCatalogColumnOptionVO(
        column.ordinalPosition(),
        column.name(),
        column.typeName(),
        column.ordinalPosition(),
        column.nullable() ? "YES" : "NO",
        column.remarks(),
        column.primaryKey() ? "PRI" : "");
  }

  private DataSourceCatalogOptionVO toOptionVO(CatalogTable table) {
    String label = isBlank(table.remarks()) ? table.name() : table.remarks();
    return new DataSourceCatalogOptionVO(table.name(), label, table.remarks());
  }

  private DataSourceQueryResultVO toPreviewVO(CatalogQueryResult result) {
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
    return new DataSourceQueryResultVO(columns, result.rows(), result.total());
  }

  private CatalogReadRequest toCatalogReadRequest(Map<String, Object> requestBody) {
    Map<String, Object> request = requireRequest(requestBody);
    String readMode = optionalText(request, "read_mode", "readMode");
    String tablePath = optionalText(request, "table_path", "tablePath", "table");
    String query = optionalText(request, "query", "sql");
    ReadMode mode =
        "sql".equalsIgnoreCase(readMode) || (!isBlank(query) && isBlank(tablePath))
            ? ReadMode.SQL
            : ReadMode.TABLE;
    try {
      return new CatalogReadRequest(mode, tablePath, query, variables(request));
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          exception.getMessage(),
          exception);
    }
  }

  private List<Variable> variables(Map<String, Object> request) {
    Object value = request.get("paramsList");
    if (!(value instanceof Iterable<?> items)) return List.of();
    List<Variable> variables = new ArrayList<>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> map)) continue;
      String name = mapText(map, "paramName", "name");
      Object rawValue = mapValue(map, "paramValue", "value");
      if (!isBlank(name)) {
        variables.add(new Variable(name, rawValue == null ? null : String.valueOf(rawValue)));
      }
    }
    return List.copyOf(variables);
  }

  private void validateReadOnlyRequest(CatalogReadRequest request) {
    if (!request.sqlMode()) return;
    String normalized = request.sql().trim();
    if (normalized.endsWith(";")) {
      normalized = normalized.substring(0, normalized.length() - 1).trim();
    }
    if (normalized.indexOf(';') >= 0 || !READ_ONLY_SELECT.matcher(normalized).matches()) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据预览仅允许执行单条 SELECT 查询");
    }
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

  private String requiredText(Map<String, Object> request, String... keys) {
    String value = optionalText(request, keys);
    if (!isBlank(value)) return value;
    throw new DataSourceException(
        DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
        keys[0] + " 不能为空");
  }

  private String optionalText(Map<String, Object> request, String... keys) {
    Object value = mapValue(request, keys);
    return value == null || String.valueOf(value).trim().isEmpty()
        ? null
        : String.valueOf(value).trim();
  }

  private String mapText(Map<?, ?> request, String... keys) {
    Object value = mapValue(request, keys);
    return value == null ? null : String.valueOf(value).trim();
  }

  private Object mapValue(Map<?, ?> request, String... keys) {
    for (String key : keys) {
      if (request.containsKey(key)) return request.get(key);
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
