package io.yak.ops.business.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.service.DataSourceCatalogService;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer.ProjectionMapping;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer.ProjectionResult;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer.SchemaColumn;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer.TableRef;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds current Dataset/DatasetField lineage from the Dataset's immutable source snapshot. */
@Service
public class DatasetLineageService {

  static final String EVIDENCE_SOURCE_TYPE = "DATASET_SOURCE_PARSE";

  private static final Logger LOGGER = LoggerFactory.getLogger(DatasetLineageService.class);
  private static final int MAX_EXPRESSION_LENGTH = 16000;

  private final LineageService lineageService;
  private final LineageMaintenanceService maintenanceService;
  private final TaskCatalogService taskCatalogService;
  private final ObjectMapper objectMapper;

  private SqlProjectionLineageAnalyzer projectionAnalyzer;
  private DataSourceCatalogService dataSourceCatalogService;

  public DatasetLineageService(
      LineageService lineageService,
      LineageMaintenanceService maintenanceService,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper) {
    this.lineageService = lineageService;
    this.maintenanceService = maintenanceService;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
  }

  /** The SQL analyzer is supplied by data-development without creating a Dataset -> development dependency. */
  @Autowired(required = false)
  void setProjectionAnalyzer(SqlProjectionLineageAnalyzer projectionAnalyzer) {
    this.projectionAnalyzer = projectionAnalyzer;
  }

  /** Catalog metadata is an optional accuracy enhancement; lineage remains best-effort without it. */
  @Autowired(required = false)
  void setDataSourceCatalogService(DataSourceCatalogService dataSourceCatalogService) {
    this.dataSourceCatalogService = dataSourceCatalogService;
  }

  public void syncCurrent(DatasetDetail detail) {
    if (detail == null || detail.dataset() == null) return;

    Dataset dataset = detail.dataset();
    String evidenceId = String.valueOf(dataset.id());
    maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

    DatasetVersion version = detail.currentVersion();
    if (version == null) {
      registerDatasetAsset(dataset, null, null, "SKIPPED", null, null, 0);
      return;
    }

    SourceSnapshot source;
    try {
      source = resolveSource(version);
    } catch (RuntimeException exception) {
      source = SourceSnapshot.failed(version.sourceType(), safeMessage(exception));
    }

    ProjectionResult projection = null;
    String parseStatus;
    String parseError = source.error();
    if (parseError != null) {
      parseStatus = "FAILED";
    } else if (source.sql() == null || source.sql().isBlank()) {
      parseStatus = "SKIPPED";
    } else if (projectionAnalyzer == null) {
      parseStatus = "SKIPPED";
      parseError = "SqlProjectionLineageAnalyzer is not available";
    } else {
      try {
        projection = projectionAnalyzer.analyze(
            source.sql(), schemaProvider(source.dataSourceId()));
        parseStatus = projection.unresolvedReferenceCount() > 0
            ? (projection.mappings().isEmpty() ? "UNRESOLVED" : "PARTIAL")
            : "SUCCESS";
      } catch (RuntimeException exception) {
        parseStatus = "FAILED";
        parseError = safeMessage(exception);
        LOGGER.warn(
            "Failed to analyze Dataset lineage for dataset {} version {}: {}",
            dataset.id(), version.versionNo(), parseError);
      }
    }

    Map<String, DatasetField> fieldsByPhysicalName = new LinkedHashMap<>();
    for (DatasetField field : detail.fields()) {
      if (field != null && field.physicalName() != null) {
        fieldsByPhysicalName.put(field.physicalName().toLowerCase(Locale.ROOT), field);
      }
    }

    int unmatchedMappings = 0;
    if (projection != null) {
      for (ProjectionMapping mapping : projection.mappings()) {
        if (!fieldsByPhysicalName.containsKey(mapping.outputColumnName().toLowerCase(Locale.ROOT))) {
          unmatchedMappings++;
        }
      }
      if (unmatchedMappings > 0 && "SUCCESS".equals(parseStatus)) parseStatus = "PARTIAL";
    }

    LineageAsset datasetAsset = registerDatasetAsset(
        dataset, version, source, parseStatus, parseError, projection, unmatchedMappings);
    Instant observedAt = version.createTime() == null ? Instant.now() : version.createTime();

    Map<String, LineageAsset> fieldAssets = new LinkedHashMap<>();
    for (DatasetField field : detail.fields()) {
      LineageAsset fieldAsset = registerDatasetFieldAsset(
          datasetAsset, dataset, version, source.dataSourceId(), field);
      fieldAssets.put(field.fieldId(), fieldAsset);
      lineageService.registerRelation(new LineageService.RegisterRelationCommand(
          fieldAsset.id(),
          datasetAsset.id(),
          LineageRelationType.CONTAINS,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          null,
          BigDecimal.ONE,
          relationVersion(version, "field:" + field.sortOrder()),
          observedAt,
          fieldContainmentProperties(version, field)));
    }

    if (source.taskAsset() != null) {
      LineageAsset task = resolveSqlTaskAsset(source, version);
      lineageService.registerRelation(new LineageService.RegisterRelationCommand(
          task.id(),
          datasetAsset.id(),
          LineageRelationType.DERIVES_FROM,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          null,
          BigDecimal.ONE,
          relationVersion(version, "source-task"),
          observedAt,
          sourceTaskProperties(version, source)));
    }

    if (projection == null) return;

    Map<String, LineageAsset> tableAssets = new LinkedHashMap<>();
    Set<String> tableRelations = new LinkedHashSet<>();
    int mappingIndex = 0;
    for (ProjectionMapping mapping : projection.mappings()) {
      mappingIndex++;
      DatasetField field = fieldsByPhysicalName.get(
          mapping.outputColumnName().toLowerCase(Locale.ROOT));
      if (field == null) continue;

      LineageAsset table = registerTableAsset(source.dataSourceId(), mapping.sourceTable(), tableAssets);
      if (tableRelations.add(mapping.sourceTable().canonicalName())) {
        lineageService.registerRelation(new LineageService.RegisterRelationCommand(
            table.id(),
            datasetAsset.id(),
            LineageRelationType.DERIVES_FROM,
            EVIDENCE_SOURCE_TYPE,
            evidenceId,
            null,
            BigDecimal.ONE,
            relationVersion(version, "table:" + tableRelations.size()),
            observedAt,
            tableRelationProperties(version, source, mapping.sourceTable())));
      }

      LineageAsset sourceColumn = registerColumnAsset(
          source.dataSourceId(), table, mapping.sourceTable(), mapping.sourceColumnName());
      LineageAsset targetField = fieldAssets.get(field.fieldId());
      lineageService.registerRelation(new LineageService.RegisterRelationCommand(
          sourceColumn.id(),
          targetField.id(),
          LineageRelationType.DERIVES_FROM,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          truncate(mapping.expression(), MAX_EXPRESSION_LENGTH),
          BigDecimal.ONE,
          relationVersion(
              version,
              "column:"
                  + mapping.outputOrdinal()
                  + ":"
                  + mapping.sourceOrdinal()
                  + ":"
                  + mappingIndex),
          observedAt,
          columnRelationProperties(version, source, field, mapping)));
    }
  }

  private SourceSnapshot resolveSource(DatasetVersion version) {
    if (version.sourceType() == DatasetSourceType.SQL_QUERY) {
      if (version.dataSourceId() == null || version.dataSourceId().isBlank()) {
        throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 dataSourceId");
      }
      if (version.sql() == null || version.sql().isBlank()) {
        throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 SQL snapshot");
      }
      return new SourceSnapshot(
          DatasetSourceType.SQL_QUERY,
          version.dataSourceId().trim(),
          version.sql(),
          null,
          null,
          null);
    }

    if (version.sourceType() == DatasetSourceType.QUERY_REVISION) {
      TaskAssetRevision resolved = taskCatalogService.resolveRevision(
          version.sourceTaskAssetId(), version.sourceTaskRevisionId());
      TaskSourceRevision revision = resolved.revision();
      if (revision.revisionNo() != version.sourceTaskRevisionNo()) {
        throw new IllegalStateException(
            "DatasetVersion source revisionNo 不一致：dataset="
                + version.sourceTaskRevisionNo()
                + ", resolved="
                + revision.revisionNo());
      }
      TaskDefinition definition = revision.definition();
      if (!"SQL".equalsIgnoreCase(definition.taskType())) {
        throw new IllegalStateException("Dataset QUERY_REVISION 来源不是 SQL：" + definition.taskType());
      }
      return new SourceSnapshot(
          DatasetSourceType.QUERY_REVISION,
          dataSourceId(definition.configJson()),
          definition.content(),
          resolved.asset(),
          revision,
          null);
    }

    return new SourceSnapshot(version.sourceType(), null, null, null, null, null);
  }

  private SqlProjectionLineageAnalyzer.SchemaProvider schemaProvider(String dataSourceId) {
    DataSourceCatalogService catalogService = this.dataSourceCatalogService;
    if (catalogService == null || dataSourceId == null || dataSourceId.isBlank()) {
      return SqlProjectionLineageAnalyzer.SchemaProvider.none();
    }

    final Long numericDataSourceId;
    try {
      numericDataSourceId = Long.valueOf(dataSourceId);
    } catch (NumberFormatException exception) {
      return SqlProjectionLineageAnalyzer.SchemaProvider.none();
    }

    Map<String, List<SchemaColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
        table.canonicalName(),
        ignored -> loadSchemaColumns(catalogService, numericDataSourceId, table));
  }

  private List<SchemaColumn> loadSchemaColumns(
      DataSourceCatalogService catalogService,
      Long dataSourceId,
      TableRef table) {
    List<DataSourceCatalogColumnVO> columns = listColumns(
        catalogService,
        dataSourceId,
        table.databaseName(),
        table.schemaName(),
        table.tableName());
    if (columns.isEmpty() && table.databaseName() == null && table.schemaName() != null) {
      columns = listColumns(
          catalogService,
          dataSourceId,
          table.schemaName(),
          null,
          table.tableName());
    }

    List<SchemaColumn> result = new ArrayList<>();
    for (DataSourceCatalogColumnVO column : columns) {
      if (column == null || column.getName() == null || column.getName().isBlank()) continue;
      result.add(new SchemaColumn(column.getName(), column.getOrdinalPosition()));
    }
    return List.copyOf(result);
  }

  private List<DataSourceCatalogColumnVO> listColumns(
      DataSourceCatalogService catalogService,
      Long dataSourceId,
      String database,
      String schema,
      String table) {
    try {
      List<DataSourceCatalogColumnVO> columns =
          catalogService.listColumns(dataSourceId, database, schema, table);
      return columns == null ? List.of() : columns;
    } catch (RuntimeException exception) {
      LOGGER.debug(
          "Dataset lineage catalog lookup failed for datasource {} table {}.{}.{}: {}",
          dataSourceId,
          database,
          schema,
          table,
          exception.getMessage());
      return List.of();
    }
  }

  private LineageAsset registerDatasetAsset(
      Dataset dataset,
      DatasetVersion version,
      SourceSnapshot source,
      String parseStatus,
      String parseError,
      ProjectionResult projection,
      int unmatchedMappings) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("status", dataset.status().name());
    if (dataset.currentVersionId() != null) {
      properties.put("currentVersionId", String.valueOf(dataset.currentVersionId()));
    }
    properties.put("lineageParseStatus", parseStatus);
    if (parseError != null && !parseError.isBlank()) {
      properties.put("lineageParseError", truncate(parseError, 1000));
    }
    if (version != null) {
      properties.put("datasetVersionId", String.valueOf(version.id()));
      properties.put("datasetVersionNo", version.versionNo());
      properties.put("sourceType", version.sourceType().name());
      properties.put("sourceTaskAssetId", String.valueOf(version.sourceTaskAssetId()));
      properties.put("sourceTaskRevisionId", String.valueOf(version.sourceTaskRevisionId()));
      properties.put("sourceTaskRevisionNo", version.sourceTaskRevisionNo());
    }
    if (source != null && source.dataSourceId() != null) {
      properties.put("dataSourceId", source.dataSourceId());
    }
    if (projection != null) {
      properties.put("candidateOutputColumnCount", projection.candidateOutputCount());
      properties.put("columnMappingCount", projection.mappings().size());
      properties.put("unresolvedColumnReferenceCount", projection.unresolvedReferenceCount());
      properties.put("unmatchedOutputMappingCount", unmatchedMappings);
    }

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        "dataset:" + dataset.id(),
        LineageAssetType.DATASET,
        dataset.name(),
        "DATASET",
        String.valueOf(dataset.id()),
        null,
        source == null ? null : source.dataSourceId(),
        null,
        null,
        null,
        null,
        properties));
  }

  private LineageAsset registerDatasetFieldAsset(
      LineageAsset datasetAsset,
      Dataset dataset,
      DatasetVersion version,
      String dataSourceId,
      DatasetField field) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("datasetId", String.valueOf(dataset.id()));
    properties.put("datasetVersionId", String.valueOf(version.id()));
    properties.put("datasetVersionNo", version.versionNo());
    properties.put("fieldId", field.fieldId());
    properties.put("physicalName", field.physicalName());
    properties.put("displayName", field.displayName());
    properties.put("dataType", field.dataType().name());
    properties.put("nullable", field.nullable());
    properties.put("defaultRole", field.defaultRole().name());
    properties.put("sortOrder", field.sortOrder());
    if (field.description() != null) properties.put("description", field.description());

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        "dataset-field:" + dataset.id() + ":" + field.fieldId(),
        LineageAssetType.DATASET_FIELD,
        field.displayName(),
        "DATASET",
        dataset.id() + ":" + field.fieldId(),
        datasetAsset.id(),
        dataSourceId,
        null,
        null,
        null,
        null,
        properties));
  }

  private LineageAsset resolveSqlTaskAsset(SourceSnapshot source, DatasetVersion version) {
    TaskAsset taskAsset = source.taskAsset();
    String key = "sql-task:data-development:" + taskAsset.sourceRef();
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("taskAssetId", String.valueOf(taskAsset.id()));
      properties.put("sourceTaskRevisionId", String.valueOf(version.sourceTaskRevisionId()));
      properties.put("sourceTaskRevisionNo", version.sourceTaskRevisionNo());
      properties.put("lineageRegistration", "DATASET_FALLBACK");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.SQL_TASK,
          taskAsset.name(),
          "DATA_DEVELOPMENT",
          taskAsset.sourceRef(),
          null,
          source.dataSourceId(),
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private LineageAsset registerTableAsset(
      String dataSourceId,
      TableRef table,
      Map<String, LineageAsset> cache) {
    String key = tableAssetKey(dataSourceId, table);
    LineageAsset cached = cache.get(key);
    if (cached != null) return cached;

    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName());
    LineageAsset registered = lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        key,
        LineageAssetType.TABLE,
        table.qualifiedName(),
        "DATASOURCE",
        dataSourceId,
        null,
        dataSourceId,
        table.databaseName(),
        table.schemaName(),
        table.tableName(),
        null,
        properties));
    cache.put(key, registered);
    return registered;
  }

  private LineageAsset registerColumnAsset(
      String dataSourceId,
      LineageAsset tableAsset,
      TableRef table,
      String columnName) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName() + "." + columnName);
    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        columnAssetKey(dataSourceId, table, columnName),
        LineageAssetType.COLUMN,
        columnName,
        "DATASOURCE",
        dataSourceId,
        tableAsset.id(),
        dataSourceId,
        table.databaseName(),
        table.schemaName(),
        table.tableName(),
        columnName,
        properties));
  }

  private JsonNode fieldContainmentProperties(DatasetVersion version, DatasetField field) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "STRUCTURE");
    properties.put("fieldId", field.fieldId());
    properties.put("physicalName", field.physicalName());
    return properties;
  }

  private JsonNode sourceTaskProperties(DatasetVersion version, SourceSnapshot source) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "DATASET");
    properties.put("sourceKind", "QUERY_REVISION");
    properties.put("taskAssetId", String.valueOf(source.taskAsset().id()));
    properties.put("taskSourceRef", source.taskAsset().sourceRef());
    return properties;
  }

  private JsonNode tableRelationProperties(
      DatasetVersion version,
      SourceSnapshot source,
      TableRef table) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "TABLE");
    properties.put("sourceKind", source.sourceType().name());
    properties.put("sourceTable", table.qualifiedName());
    return properties;
  }

  private JsonNode columnRelationProperties(
      DatasetVersion version,
      SourceSnapshot source,
      DatasetField field,
      ProjectionMapping mapping) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "COLUMN");
    properties.put("sourceKind", source.sourceType().name());
    properties.put("fieldId", field.fieldId());
    properties.put("outputColumn", mapping.outputColumnName());
    properties.put("mappingKind", mapping.mappingKind().name());
    properties.put("outputOrdinal", mapping.outputOrdinal());
    properties.put("sourceOrdinal", mapping.sourceOrdinal());
    properties.put("sourceTable", mapping.sourceTable().qualifiedName());
    properties.put("sourceColumn", mapping.sourceColumnName());
    return properties;
  }

  private ObjectNode versionProperties(DatasetVersion version) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("datasetVersionId", String.valueOf(version.id()));
    properties.put("datasetVersionNo", version.versionNo());
    properties.put("sourceType", version.sourceType().name());
    properties.put("sourceTaskAssetId", String.valueOf(version.sourceTaskAssetId()));
    properties.put("sourceTaskRevisionId", String.valueOf(version.sourceTaskRevisionId()));
    properties.put("sourceTaskRevisionNo", version.sourceTaskRevisionNo());
    return properties;
  }

  private String dataSourceId(String configJson) {
    try {
      JsonNode root = objectMapper.readTree(
          configJson == null || configJson.isBlank() ? "{}" : configJson);
      JsonNode value = root == null ? null : root.get("dataSourceId");
      String dataSourceId = value == null || value.isNull() ? null : value.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException("Dataset source SQL dataSourceId 不能为空");
      }
      return dataSourceId.trim();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Dataset source SQL configJson 不是合法 JSON", exception);
    }
  }

  private static String tableAssetKey(String dataSourceId, TableRef table) {
    return "table:" + dataSourceId + ":" + table.canonicalName();
  }

  private static String columnAssetKey(
      String dataSourceId,
      TableRef table,
      String columnName) {
    return "column:"
        + dataSourceId
        + ":"
        + table.canonicalName()
        + "."
        + columnName.toLowerCase(Locale.ROOT);
  }

  private static String relationVersion(DatasetVersion version, String suffix) {
    return "dataset-v" + version.versionNo() + ":" + suffix;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown lineage error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private record SourceSnapshot(
      DatasetSourceType sourceType,
      String dataSourceId,
      String sql,
      TaskAsset taskAsset,
      TaskSourceRevision taskRevision,
      String error) {

    static SourceSnapshot failed(DatasetSourceType sourceType, String error) {
      return new SourceSnapshot(sourceType, null, null, null, null, error);
    }
  }
}
