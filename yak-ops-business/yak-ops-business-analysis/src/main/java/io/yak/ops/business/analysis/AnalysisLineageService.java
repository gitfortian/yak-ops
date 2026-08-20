package io.yak.ops.business.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Builds Dataset/DatasetField -> reusable Analysis Chart lineage. */
@Service
public class AnalysisLineageService {

  static final String EVIDENCE_SOURCE_TYPE = "ANALYSIS_BINDING";

  private final LineageService lineageService;
  private final LineageMaintenanceService maintenanceService;
  private final ObjectMapper objectMapper;

  public AnalysisLineageService(
      LineageService lineageService,
      LineageMaintenanceService maintenanceService,
      ObjectMapper objectMapper) {
    this.lineageService = lineageService;
    this.maintenanceService = maintenanceService;
    this.objectMapper = objectMapper;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void syncCurrent(AnalysisAsset analysis) {
    if (analysis == null || analysis.id() <= 0L) return;

    String evidenceId = String.valueOf(analysis.id());
    maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

    LineageAsset chart = registerChartAsset(analysis);
    LineageAsset dataset = resolveDatasetAsset(analysis.datasetId());
    Instant observedAt = analysis.updateTime() == null ? Instant.now() : analysis.updateTime();

    lineageService.registerRelation(new LineageService.RegisterRelationCommand(
        dataset.id(),
        chart.id(),
        LineageRelationType.CONSUMES,
        EVIDENCE_SOURCE_TYPE,
        evidenceId,
        null,
        BigDecimal.ONE,
        "analysis-current:dataset",
        observedAt,
        datasetRelationProperties(analysis)));

    Map<String, FieldUsage> usages = fieldUsages(analysis.querySpec());
    int index = 0;
    for (Map.Entry<String, FieldUsage> entry : usages.entrySet()) {
      index++;
      String fieldId = entry.getKey();
      LineageAsset field = resolveDatasetFieldAsset(dataset, analysis.datasetId(), fieldId);
      lineageService.registerRelation(new LineageService.RegisterRelationCommand(
          field.id(),
          chart.id(),
          LineageRelationType.CONSUMES,
          EVIDENCE_SOURCE_TYPE,
          evidenceId,
          null,
          BigDecimal.ONE,
          "analysis-current:field:" + index,
          observedAt,
          fieldRelationProperties(analysis, fieldId, entry.getValue())));
    }
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void clear(long analysisId) {
    if (analysisId <= 0L) return;
    maintenanceService.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, String.valueOf(analysisId));
  }

  private LineageAsset registerChartAsset(AnalysisAsset analysis) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("chartType", analysis.chartType().name());
    if (analysis.description() != null) properties.put("description", analysis.description());
    if (analysis.updateTime() != null) properties.put("analysisUpdatedAt", analysis.updateTime().toString());
    properties.put("bindingMode", "REUSABLE_ANALYSIS");

    return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
        chartAssetKey(analysis.id()),
        LineageAssetType.CHART,
        analysis.name(),
        "ANALYSIS",
        String.valueOf(analysis.id()),
        null,
        null,
        null,
        null,
        null,
        null,
        properties));
  }

  private LineageAsset resolveDatasetAsset(long datasetId) {
    String key = datasetAssetKey(datasetId);
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("lineageRegistration", "ANALYSIS_FALLBACK");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.DATASET,
          key,
          "DATASET",
          String.valueOf(datasetId),
          null,
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private LineageAsset resolveDatasetFieldAsset(
      LineageAsset dataset,
      long datasetId,
      String fieldId) {
    String key = datasetFieldAssetKey(datasetId, fieldId);
    try {
      return lineageService.getAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("datasetId", String.valueOf(datasetId));
      properties.put("fieldId", fieldId);
      properties.put("lineageRegistration", "ANALYSIS_FALLBACK");
      return lineageService.registerAsset(new LineageService.RegisterAssetCommand(
          key,
          LineageAssetType.DATASET_FIELD,
          fieldId,
          "DATASET",
          datasetId + ":" + fieldId,
          dataset.id(),
          null,
          null,
          null,
          null,
          null,
          properties));
    }
  }

  private Map<String, FieldUsage> fieldUsages(AnalysisQuerySpec querySpec) {
    Map<String, FieldUsage> result = new LinkedHashMap<>();
    if (querySpec == null) return result;

    if (querySpec.dimensions() != null) {
      querySpec.dimensions().forEach(fieldId -> usage(result, fieldId).roles().add("DIMENSION"));
    }
    if (querySpec.metrics() != null) {
      querySpec.metrics().forEach(metric -> {
        if (metric == null) return;
        FieldUsage usage = usage(result, metric.fieldId());
        usage.roles().add("METRIC");
        if (metric.aggregation() != null) usage.metricAggregations().add(metric.aggregation().name());
      });
    }
    if (querySpec.filters() != null) {
      querySpec.filters().forEach(filter -> {
        if (filter != null) usage(result, filter.fieldId()).roles().add("FILTER");
      });
    }
    if (querySpec.sorts() != null) {
      querySpec.sorts().forEach(sort -> {
        if (sort == null) return;
        FieldUsage usage = usage(result, sort.fieldId());
        usage.roles().add("SORT");
        if (sort.aggregation() != null) usage.sortAggregations().add(sort.aggregation().name());
      });
    }
    return result;
  }

  private FieldUsage usage(Map<String, FieldUsage> usages, String fieldId) {
    if (fieldId == null || fieldId.isBlank()) {
      throw new IllegalStateException("Analysis lineage fieldId 不能为空");
    }
    return usages.computeIfAbsent(
        fieldId.trim(),
        ignored -> new FieldUsage(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>()));
  }

  private ObjectNode datasetRelationProperties(AnalysisAsset analysis) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("lineageLevel", "DATASET");
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("chartType", analysis.chartType().name());
    return properties;
  }

  private ObjectNode fieldRelationProperties(
      AnalysisAsset analysis,
      String fieldId,
      FieldUsage usage) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("lineageLevel", "DATASET_FIELD");
    properties.put("analysisId", String.valueOf(analysis.id()));
    properties.put("datasetId", String.valueOf(analysis.datasetId()));
    properties.put("fieldId", fieldId);

    ArrayNode roles = properties.putArray("usageRoles");
    usage.roles().forEach(roles::add);
    if (!usage.metricAggregations().isEmpty()) {
      ArrayNode aggregations = properties.putArray("metricAggregations");
      usage.metricAggregations().forEach(aggregations::add);
    }
    if (!usage.sortAggregations().isEmpty()) {
      ArrayNode aggregations = properties.putArray("sortAggregations");
      usage.sortAggregations().forEach(aggregations::add);
    }
    return properties;
  }

  static String chartAssetKey(long analysisId) {
    return "chart:analysis:" + analysisId;
  }

  static String datasetAssetKey(long datasetId) {
    return "dataset:" + datasetId;
  }

  static String datasetFieldAssetKey(long datasetId, String fieldId) {
    return "dataset-field:" + datasetId + ":" + fieldId;
  }

  private record FieldUsage(
      Set<String> roles,
      Set<String> metricAggregations,
      Set<String> sortAggregations) {
  }
}
