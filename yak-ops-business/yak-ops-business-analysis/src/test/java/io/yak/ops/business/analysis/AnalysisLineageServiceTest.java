package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalysisLineageServiceTest {

  @Test
  void syncBuildsStableDatasetAndFieldConsumptionAndMergesUsageRoles() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    ObjectMapper objectMapper = new ObjectMapper();
    AtomicLong ids = new AtomicLong(100L);

    when(lineage.getAssetByKey(anyString())).thenThrow(new IllegalArgumentException("missing"));
    when(lineage.registerAsset(any())).thenAnswer(invocation ->
        asset(ids.incrementAndGet(), invocation.getArgument(0)));

    AnalysisLineageService service = new AnalysisLineageService(lineage, maintenance, objectMapper);
    AnalysisAsset analysis = new AnalysisAsset(
        7L,
        "区域销售",
        "销售分析",
        21L,
        AnalysisChartType.BAR,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(new AnalysisFilterBinding("region", AnalysisFilterOperator.EQ, "华南")),
            List.of(new AnalysisSortBinding("amount", AnalysisAggregation.SUM, AnalysisSortDirection.DESC)),
            500,
            30),
        new AnalysisVisualConfig(false, false, false, true),
        Instant.EPOCH,
        Instant.EPOCH);

    service.syncCurrent(analysis);

    verify(maintenance).clearRelationsByEvidence(AnalysisLineageService.EVIDENCE_SOURCE_TYPE, "7");

    ArgumentCaptor<LineageService.RegisterAssetCommand> assets =
        ArgumentCaptor.forClass(LineageService.RegisterAssetCommand.class);
    verify(lineage, org.mockito.Mockito.atLeast(4)).registerAsset(assets.capture());
    assertTrue(assets.getAllValues().stream().anyMatch(command ->
        "chart:analysis:7".equals(command.assetKey()) && command.assetType() == LineageAssetType.CHART));
    assertTrue(assets.getAllValues().stream().anyMatch(command ->
        "dataset:21".equals(command.assetKey()) && command.assetType() == LineageAssetType.DATASET));
    assertTrue(assets.getAllValues().stream().anyMatch(command ->
        "dataset-field:21:region".equals(command.assetKey())
            && command.assetType() == LineageAssetType.DATASET_FIELD));

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage, org.mockito.Mockito.times(3)).registerRelation(relations.capture());
    assertTrue(relations.getAllValues().stream().allMatch(relation ->
        relation.relationType() == LineageRelationType.CONSUMES));

    LineageService.RegisterRelationCommand region = relations.getAllValues().stream()
        .filter(relation -> relation.properties() != null
            && "region".equals(relation.properties().path("fieldId").asText()))
        .findFirst()
        .orElseThrow();
    List<String> roles = new java.util.ArrayList<>();
    region.properties().path("usageRoles").forEach(node -> roles.add(node.asText()));
    assertEquals(List.of("DIMENSION", "FILTER"), roles);

    LineageService.RegisterRelationCommand amount = relations.getAllValues().stream()
        .filter(relation -> relation.properties() != null
            && "amount".equals(relation.properties().path("fieldId").asText()))
        .findFirst()
        .orElseThrow();
    assertEquals("SUM", amount.properties().path("metricAggregations").get(0).asText());
  }

  @Test
  void clearOnlyRemovesAnalysisScopedEvidence() {
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    AnalysisLineageService service = new AnalysisLineageService(
        mock(LineageService.class), maintenance, new ObjectMapper());

    service.clear(19L);

    verify(maintenance).clearRelationsByEvidence(
        AnalysisLineageService.EVIDENCE_SOURCE_TYPE, "19");
  }

  private static LineageAsset asset(
      long id,
      LineageService.RegisterAssetCommand command) {
    return new LineageAsset(
        id,
        command.assetKey(),
        command.assetType(),
        command.name(),
        command.sourceType(),
        command.sourceId(),
        command.parentAssetId(),
        command.dataSourceId(),
        command.databaseName(),
        command.schemaName(),
        command.tableName(),
        command.columnName(),
        command.properties(),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
