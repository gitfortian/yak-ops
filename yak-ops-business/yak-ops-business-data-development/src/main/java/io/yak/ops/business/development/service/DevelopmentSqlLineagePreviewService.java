package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.service.DataSourceCatalogService;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview.ColumnMapping;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview.PreviewAsset;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview.PreviewGraph;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview.PreviewRelation;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageDirection;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds editor-time SQL lineage without registering assets or relations in Lineage Core. */
@Service
public class DevelopmentSqlLineagePreviewService {

  private static final String PREVIEW_SOURCE_TYPE = "DATA_DEVELOPMENT_SQL_PREVIEW";

  private final DevelopmentNodeRepository nodeRepository;
  private final SqlTableLineageParser tableParser;
  private final SqlColumnLineageParser columnParser;
  private final SqlProjectionLineageAnalyzer projectionAnalyzer;
  private final ObjectMapper objectMapper;

  private DataSourceCatalogService dataSourceCatalogService;

  public DevelopmentSqlLineagePreviewService(
      DevelopmentNodeRepository nodeRepository,
      SqlTableLineageParser tableParser,
      SqlColumnLineageParser columnParser,
      SqlProjectionLineageAnalyzer projectionAnalyzer,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.tableParser = tableParser;
    this.columnParser = columnParser;
    this.projectionAnalyzer = projectionAnalyzer;
    this.objectMapper = objectMapper;
  }

  @Autowired(required = false)
  void setDataSourceCatalogService(DataSourceCatalogService dataSourceCatalogService) {
    this.dataSourceCatalogService = dataSourceCatalogService;
  }

  public DevelopmentSqlLineagePreview preview(
      Long nodeId,
      String taskType,
      String sql,
      String configJson) {
    DevelopmentNode node = requireSqlNode(nodeId, taskType);
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }
    String dataSourceId = dataSourceId(configJson);
    PreviewAsset task = taskAsset(node, dataSourceId);

    final SqlTableLineageParser.ParseResult tableParsed;
    try {
      tableParsed = tableParser.parse(sql);
    } catch (RuntimeException exception) {
      return failedPreview(task, dataSourceId, safeMessage(exception));
    }

    ColumnAnalysis columnAnalysis = analyzeColumns(sql, dataSourceId, tableParsed.outputs().isEmpty());
    Map<String, PreviewAsset> nodes = new LinkedHashMap<>();
    Map<String, PreviewRelation> relations = new LinkedHashMap<>();
    nodes.put(task.id(), task);

    for (SqlTableLineageParser.TableRef input : tableParsed.inputs()) {
      PreviewAsset table = tableAsset(dataSourceId, input);
      nodes.putIfAbsent(table.id(), table);
      PreviewRelation relation = tableRelation(table, task, LineageRelationType.READS_FROM, "INPUT", node.id());
      relations.putIfAbsent(relation.id(), relation);
    }
    for (SqlTableLineageParser.TableRef output : tableParsed.outputs()) {
      PreviewAsset table = tableAsset(dataSourceId, output);
      nodes.putIfAbsent(table.id(), table);
      PreviewRelation relation = tableRelation(task, table, LineageRelationType.WRITES_TO, "OUTPUT", node.id());
      relations.putIfAbsent(relation.id(), relation);
    }

    String status;
    if (columnAnalysis.error() != null) {
      status = "PARTIAL";
    } else if (columnAnalysis.unresolvedReferenceCount() > 0) {
      status = columnAnalysis.mappings().isEmpty() ? "UNRESOLVED" : "PARTIAL";
    } else {
      status = "SUCCESS";
    }

    return new DevelopmentSqlLineagePreview(
        status,
        dataSourceId,
        tableParsed.statementCount(),
        tableParsed.inputs().size(),
        tableParsed.outputs().size(),
        columnAnalysis.mappings().size(),
        columnAnalysis.candidateOutputCount(),
        columnAnalysis.unresolvedReferenceCount(),
        null,
        columnAnalysis.error(),
        new PreviewGraph(
            task,
            LineageDirection.BOTH,
            1,
            List.copyOf(nodes.values()),
            List.copyOf(relations.values())),
        columnAnalysis.mappings());
  }

  private DevelopmentNode requireSqlNode(Long nodeId, String taskType) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
    if (!"SQL".equalsIgnoreCase(node.type())) {
      throw new IllegalArgumentException("只有 SQL 节点支持血缘预览：" + node.type());
    }
    if (taskType == null || !"SQL".equalsIgnoreCase(taskType.trim())) {
      throw new IllegalArgumentException("血缘预览 taskType 必须为 SQL");
    }
    return node;
  }

  private ColumnAnalysis analyzeColumns(String sql, String dataSourceId, boolean projectionOnly) {
    if (projectionOnly) {
      try {
        SqlProjectionLineageAnalyzer.ProjectionResult result = projectionAnalyzer.analyze(
            sql,
            projectionSchemaProvider(dataSourceId));
        List<ColumnMapping> mappings = result.mappings().stream()
            .map(mapping -> new ColumnMapping(
                mapping.sourceTable().qualifiedName(),
                mapping.sourceColumnName(),
                null,
                mapping.outputColumnName(),
                mapping.mappingKind().name(),
                mapping.expression(),
                mapping.outputOrdinal(),
                mapping.sourceOrdinal()))
            .toList();
        return new ColumnAnalysis(
            mappings,
            result.candidateOutputCount(),
            result.unresolvedReferenceCount(),
            null);
      } catch (RuntimeException projectionFailure) {
        // Non-projection statements can also have no write target. Fall through to the baseline
        // parser before surfacing a column-level preview failure.
        try {
          return analyzePhysicalColumns(sql, dataSourceId);
        } catch (RuntimeException ignored) {
          return new ColumnAnalysis(List.of(), 0, 0, safeMessage(projectionFailure));
        }
      }
    }

    try {
      return analyzePhysicalColumns(sql, dataSourceId);
    } catch (RuntimeException exception) {
      return new ColumnAnalysis(List.of(), 0, 0, safeMessage(exception));
    }
  }

  private ColumnAnalysis analyzePhysicalColumns(String sql, String dataSourceId) {
    SqlColumnLineageParser.ParseResult result = columnParser.parse(sql, columnSchemaProvider(dataSourceId));
    List<ColumnMapping> mappings = result.mappings().stream()
        .map(mapping -> new ColumnMapping(
            mapping.sourceTable().qualifiedName(),
            mapping.sourceColumnName(),
            mapping.targetTable().qualifiedName(),
            mapping.targetColumnName(),
            mapping.mappingKind().name(),
            mapping.expression(),
            mapping.outputOrdinal(),
            mapping.sourceOrdinal()))
        .toList();
    return new ColumnAnalysis(
        mappings,
        result.candidateOutputCount(),
        result.unresolvedReferenceCount(),
        null);
  }

  private SqlColumnLineageParser.SchemaProvider columnSchemaProvider(String dataSourceId) {
    Map<String, List<CatalogColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
            table.canonicalName(),
            ignored -> catalogColumns(
                dataSourceId,
                table.databaseName(),
                table.schemaName(),
                table.tableName()))
        .stream()
        .map(column -> new SqlColumnLineageParser.SchemaColumn(column.name(), column.ordinalPosition()))
        .toList();
  }

  private SqlProjectionLineageAnalyzer.SchemaProvider projectionSchemaProvider(String dataSourceId) {
    Map<String, List<CatalogColumn>> cache = new LinkedHashMap<>();
    return table -> cache.computeIfAbsent(
            table.canonicalName(),
            ignored -> catalogColumns(
                dataSourceId,
                table.databaseName(),
                table.schemaName(),
                table.tableName()))
        .stream()
        .map(column -> new SqlProjectionLineageAnalyzer.SchemaColumn(column.name(), column.ordinalPosition()))
        .toList();
  }

  private List<CatalogColumn> catalogColumns(
      String dataSourceId,
      String database,
      String schema,
      String table) {
    DataSourceCatalogService catalogService = this.dataSourceCatalogService;
    if (catalogService == null) return List.of();

    final Long numericDataSourceId;
    try {
      numericDataSourceId = Long.valueOf(dataSourceId);
    } catch (NumberFormatException exception) {
      return List.of();
    }

    List<DataSourceCatalogColumnVO> columns = listColumns(
        catalogService,
        numericDataSourceId,
        database,
        schema,
        table);
    if (columns.isEmpty() && database == null && schema != null) {
      columns = listColumns(catalogService, numericDataSourceId, schema, null, table);
    }

    List<CatalogColumn> result = new ArrayList<>();
    for (DataSourceCatalogColumnVO column : columns) {
      if (column == null || column.getName() == null || column.getName().isBlank()) continue;
      result.add(new CatalogColumn(column.getName(), column.getOrdinalPosition()));
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
      List<DataSourceCatalogColumnVO> columns = catalogService.listColumns(
          dataSourceId,
          database,
          schema,
          table);
      return columns == null ? List.of() : columns;
    } catch (RuntimeException exception) {
      return List.of();
    }
  }

  private PreviewAsset taskAsset(DevelopmentNode node, String dataSourceId) {
    String key = "sql-task:data-development:" + node.id();
    return new PreviewAsset(
        key,
        key,
        LineageAssetType.SQL_TASK,
        node.name(),
        "DATA_DEVELOPMENT",
        String.valueOf(node.id()),
        null,
        dataSourceId,
        null,
        null,
        null,
        null,
        Map.of("preview", true));
  }

  private PreviewAsset tableAsset(String dataSourceId, SqlTableLineageParser.TableRef table) {
    String key = "table:" + dataSourceId + ":" + table.canonicalName();
    return new PreviewAsset(
        key,
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
        Map.of("qualifiedName", table.qualifiedName(), "preview", true));
  }

  private PreviewRelation tableRelation(
      PreviewAsset source,
      PreviewAsset target,
      LineageRelationType relationType,
      String role,
      long nodeId) {
    String id = source.id() + "->" + target.id() + ":" + relationType.name();
    return new PreviewRelation(
        id,
        source.id(),
        target.id(),
        relationType,
        PREVIEW_SOURCE_TYPE,
        String.valueOf(nodeId),
        null,
        Map.of("lineageLevel", "TABLE", "tableRole", role, "preview", true));
  }

  private DevelopmentSqlLineagePreview failedPreview(
      PreviewAsset task,
      String dataSourceId,
      String parseError) {
    return new DevelopmentSqlLineagePreview(
        "FAILED",
        dataSourceId,
        0,
        0,
        0,
        0,
        0,
        0,
        parseError,
        null,
        new PreviewGraph(task, LineageDirection.BOTH, 1, List.of(task), List.of()),
        List.of());
  }

  private String dataSourceId(String configJson) {
    try {
      JsonNode root = objectMapper.readTree(
          configJson == null || configJson.isBlank() ? "{}" : configJson);
      JsonNode value = root == null ? null : root.get("dataSourceId");
      String dataSourceId = value == null || value.isNull() ? null : value.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException("SQL task dataSourceId 不能为空");
      }
      return dataSourceId.trim();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("SQL task configJson 不是合法 JSON", exception);
    }
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown parser error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private record CatalogColumn(String name, Integer ordinalPosition) {
  }

  private record ColumnAnalysis(
      List<ColumnMapping> mappings,
      int candidateOutputCount,
      int unresolvedReferenceCount,
      String error) {
  }
}
