package io.yak.ops.business.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Discovers Dataset output schema from either legacy TaskRevision or Dataset-owned SQL. */
@Service
final class DatasetSchemaDiscoveryService {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_DISCOVERY_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_PREVIEW_MAX_ROWS = 1_000;
  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

  private final TaskCatalogService taskCatalogService;
  private final DataSourceExecutionProvider dataSourceExecutionProvider;
  private final ObjectMapper objectMapper;

  DatasetSchemaDiscoveryService(
      TaskCatalogService taskCatalogService,
      DataSourceExecutionProvider dataSourceExecutionProvider,
      ObjectMapper objectMapper) {
    this.taskCatalogService = taskCatalogService;
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
    this.objectMapper = objectMapper;
  }

  List<DatasetService.FieldSpec> discover(long datasetId, TaskAsset asset) {
    return toFields(datasetId, discoverColumns(asset));
  }

  List<DatasetService.FieldSpec> preview(TaskAsset asset) {
    return toPreviewFields(discoverColumns(asset));
  }

  List<DatasetService.FieldSpec> discover(long datasetId, String dataSourceId, String sql) {
    return toFields(datasetId, discoverColumns(dataSourceId, sql, DEFAULT_TIMEOUT_SECONDS));
  }

  /** Preview owns no persistent fieldId; stable ids are assigned when a version is saved. */
  List<DatasetService.FieldSpec> preview(String dataSourceId, String sql) {
    return toPreviewFields(discoverColumns(dataSourceId, sql, DEFAULT_TIMEOUT_SECONDS));
  }

  /** Editor query preview returns real rows and derives the field contract from the same result set. */
  QueryPreview previewQuery(String dataSourceId, String sql) {
    String normalizedDataSourceId = requireDataSourceId(dataSourceId);
    String baseSql = DatasetSqlSafety.requireReadOnlyQuery(sql);
    long started = System.nanoTime();
    DataSourceSqlResult result;
    try (DataSourceSqlExecutor executor = dataSourceExecutionProvider.open(normalizedDataSourceId)) {
      result = executor.execute(new DataSourceSqlRequest(
          baseSql,
          DEFAULT_PREVIEW_MAX_ROWS,
          DEFAULT_TIMEOUT_SECONDS));
    }
    long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    if (!result.resultSet()) throw new IllegalStateException("Dataset 查询没有返回结果集");
    if (result.columns().isEmpty()) {
      throw new IllegalArgumentException("Dataset 来源查询没有可发现的输出字段");
    }
    return new QueryPreview(toPreviewFields(result.columns()), result, durationMs);
  }

  private List<DataSourceSqlColumn> discoverColumns(TaskAsset asset) {
    TaskAssetRevision resolved = taskCatalogService.resolveRevision(
        asset.id(), asset.currentRevision().taskRevisionId());
    if (resolved.revision().revisionId() != asset.currentRevision().taskRevisionId()
        || resolved.revision().revisionNo() != asset.currentRevision().revisionNo()) {
      throw new IllegalStateException("Schema discovery 解析到的 TaskRevision 与发布快照不一致");
    }
    TaskDefinition definition = resolved.revision().definition();
    if (!"SQL".equalsIgnoreCase(definition.taskType())) {
      throw new IllegalArgumentException("Schema discovery 仅支持 SQL TaskRevision");
    }
    SourceConfig config = sourceConfig(definition.configJson());
    return discoverColumns(config.dataSourceId(), definition.content(), config.timeoutSeconds());
  }

  private List<DataSourceSqlColumn> discoverColumns(
      String dataSourceId,
      String sql,
      int timeoutSeconds) {
    String normalizedDataSourceId = requireDataSourceId(dataSourceId);
    String baseSql = DatasetSqlSafety.requireReadOnlyQuery(sql);
    String discoverySql = "SELECT yak_dataset_source.* FROM (" + baseSql
        + ") yak_dataset_source LIMIT 1";

    DataSourceSqlResult result;
    try (DataSourceSqlExecutor executor = dataSourceExecutionProvider.open(normalizedDataSourceId)) {
      result = executor.execute(new DataSourceSqlRequest(
          discoverySql, 1, Math.min(Math.max(timeoutSeconds, 1), MAX_DISCOVERY_TIMEOUT_SECONDS)));
    }
    if (!result.resultSet()) throw new IllegalStateException("Dataset schema discovery 没有返回结果集");
    if (result.columns().isEmpty()) {
      throw new IllegalArgumentException("Dataset 来源查询没有可发现的输出字段");
    }
    return result.columns();
  }

  private List<DatasetService.FieldSpec> toFields(long datasetId, List<DataSourceSqlColumn> columns) {
    return toPreviewFields(columns).stream()
        .map(field -> new DatasetService.FieldSpec(
            stableFieldId(datasetId, field.physicalName()),
            field.physicalName(), field.displayName(), field.dataType(), field.nullable(),
            field.description(), field.defaultRole()))
        .toList();
  }

  private List<DatasetService.FieldSpec> toPreviewFields(List<DataSourceSqlColumn> columns) {
    List<DatasetService.FieldSpec> fields = new ArrayList<>(columns.size());
    Set<String> names = new HashSet<>();
    for (DataSourceSqlColumn column : columns) {
      String physicalName = column.label();
      if (physicalName == null || physicalName.isBlank()) physicalName = column.name();
      if (physicalName == null || physicalName.isBlank()) {
        throw new IllegalArgumentException("Dataset 来源查询存在无名称字段，请在 SQL 中显式设置别名");
      }
      physicalName = physicalName.trim();
      if (!SAFE_IDENTIFIER.matcher(physicalName).matches()) {
        throw new IllegalArgumentException(
            "Dataset 输出字段必须使用简单安全别名 [A-Za-z_][A-Za-z0-9_$]*：" + physicalName);
      }
      if (!names.add(physicalName.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("Dataset 来源查询存在重复输出字段：" + physicalName);
      }
      DatasetFieldDataType dataType = dataType(column.jdbcType());
      DatasetFieldRole role = dataType == DatasetFieldDataType.NUMBER
          ? DatasetFieldRole.MEASURE : DatasetFieldRole.DIMENSION;
      fields.add(new DatasetService.FieldSpec(
          null, physicalName, physicalName, dataType, column.nullable(), null, role));
    }
    return List.copyOf(fields);
  }

  private String stableFieldId(long datasetId, String physicalName) {
    String normalized = physicalName.trim().toLowerCase(Locale.ROOT);
    return UUID.nameUUIDFromBytes(
        ("dataset:" + datasetId + ":" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private DatasetFieldDataType dataType(int jdbcType) {
    return switch (jdbcType) {
      case Types.BOOLEAN, Types.BIT -> DatasetFieldDataType.BOOLEAN;
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
          Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> DatasetFieldDataType.NUMBER;
      case Types.DATE -> DatasetFieldDataType.DATE;
      case Types.TIME, Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
          DatasetFieldDataType.DATETIME;
      case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
          Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> DatasetFieldDataType.STRING;
      default -> DatasetFieldDataType.UNKNOWN;
    };
  }

  private SourceConfig sourceConfig(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) throw new IllegalArgumentException("SQL configJson 必须是 JSON 对象");
      String dataSourceId = root.path("dataSourceId").asText(null);
      int sourceTimeout = root.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS);
      if (sourceTimeout < 1) sourceTimeout = DEFAULT_TIMEOUT_SECONDS;
      return new SourceConfig(
          requireDataSourceId(dataSourceId),
          Math.min(sourceTimeout, MAX_DISCOVERY_TIMEOUT_SECONDS));
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL TaskRevision configJson 非法", exception);
    }
  }

  private String requireDataSourceId(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Dataset 必须选择数据源");
    return value.trim();
  }

  record QueryPreview(
      List<DatasetService.FieldSpec> fields,
      DataSourceSqlResult result,
      long durationMs) {
  }

  private record SourceConfig(String dataSourceId, int timeoutSeconds) {
  }
}
