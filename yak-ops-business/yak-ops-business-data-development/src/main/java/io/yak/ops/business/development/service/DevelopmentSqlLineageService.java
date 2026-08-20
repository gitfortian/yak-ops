package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Publishes authoritative table-level lineage for immutable SQL task revisions. */
@Service
public class DevelopmentSqlLineageService {

  static final String EVIDENCE_SOURCE_TYPE = "DATA_DEVELOPMENT_SQL_PARSE";

  private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentSqlLineageService.class);
  private static final int MAX_EVIDENCE_SQL_LENGTH = 16000;

  private final LineageService lineageService;
  private final LineageMaintenanceService maintenanceService;
  private final SqlTableLineageParser parser;
  private final ObjectMapper objectMapper;

  public DevelopmentSqlLineageService(
      LineageService lineageService,
      LineageMaintenanceService maintenanceService,
      SqlTableLineageParser parser,
      ObjectMapper objectMapper) {
    this.lineageService = lineageService;
    this.maintenanceService = maintenanceService;
    this.parser = parser;
    this.objectMapper = objectMapper;
  }

  public void syncPublished(DevelopmentNode node, DevelopmentTaskRevision revision) {
    if (node == null || revision == null || revision.definition() == null) return;
    if (!"SQL".equalsIgnoreCase(revision.definition().taskType())) return;

    String evidenceId = String.valueOf(node.id());
    String dataSourceId = dataSourceId(revision.definition().configJson());
    String sql = revision.definition().content();

    try {
      SqlTableLineageParser.ParseResult parsed = parser.parse(sql);
      maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

      LineageAsset task = registerTaskAsset(node, revision, dataSourceId, parsed, null);
      String evidenceSql = truncate(sql, MAX_EVIDENCE_SQL_LENGTH);
      Instant observedAt = revision.createTime() == null ? Instant.now() : revision.createTime();

      for (SqlTableLineageParser.TableRef input : parsed.inputs()) {
        LineageAsset table = registerTableAsset(dataSourceId, input);
        lineageService.registerRelation(new LineageService.RegisterRelationCommand(
            table.id(),
            task.id(),
            LineageRelationType.READS_FROM,
            EVIDENCE_SOURCE_TYPE,
            evidenceId,
            evidenceSql,
            BigDecimal.ONE,
            Integer.toString(revision.revisionNo()),
            observedAt,
            relationProperties(revision, "INPUT")));
      }

      for (SqlTableLineageParser.TableRef output : parsed.outputs()) {
        LineageAsset table = registerTableAsset(dataSourceId, output);
        lineageService.registerRelation(new LineageService.RegisterRelationCommand(
            task.id(),
            table.id(),
            LineageRelationType.WRITES_TO,
            EVIDENCE_SOURCE_TYPE,
            evidenceId,
            evidenceSql,
            BigDecimal.ONE,
            Integer.toString(revision.revisionNo()),
            observedAt,
            relationProperties(revision, "OUTPUT")));
      }
    } catch (SqlTableLineageParser.SqlLineageParseException exception) {
      maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);
      registerTaskAsset(node, revision, dataSourceId, null, exception);
      LOGGER.warn(
          "Failed to parse SQL lineage for development node {} revision {}: {}",
          node.id(),
          revision.revisionNo(),
          exception.getMessage());
    }
  }

  private LineageAsset registerTaskAsset(
      DevelopmentNode node,
      DevelopmentTaskRevision revision,
      String dataSourceId,
      SqlTableLineageParser.ParseResult parsed,
      RuntimeException parseFailure) {
    ObjectNode properties = objectMapper.createObjectNode();
    if (node.projectId() != null) properties.put("projectId", String.valueOf(node.projectId()));
    properties.put("revisionId", String.valueOf(revision.id()));
    properties.put("revisionNo", revision.revisionNo());
    properties.put("checksum", revision.checksum());
    properties.put("dataSourceId", dataSourceId);
    if (parseFailure == null && parsed != null) {
      properties.put("parseStatus", "SUCCESS");
      properties.put("statementCount", parsed.statementCount());
      properties.put("inputTableCount", parsed.inputs().size());
      properties.put("outputTableCount", parsed.outputs().size());
    } else {
      properties.put("parseStatus", "FAILED");
      properties.put("parseError", truncate(
          parseFailure == null ? "Unknown SQL lineage parser failure" : parseFailure.getMessage(),
          1000));
    }

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        "sql-task:data-development:" + node.id(),
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
        properties));
  }

  private LineageAsset registerTableAsset(
      String dataSourceId,
      SqlTableLineageParser.TableRef table) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName());

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        "table:" + dataSourceId + ":" + table.canonicalName(),
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
  }

  private JsonNode relationProperties(DevelopmentTaskRevision revision, String role) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("revisionId", String.valueOf(revision.id()));
    properties.put("revisionNo", revision.revisionNo());
    properties.put("tableRole", role);
    return properties;
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

  private static String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
