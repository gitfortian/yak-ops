package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardLineageServiceTest {

  @Test
  void syncBuildsLinkedAndInlineChartLineageAndKeepsRepeatedWidgetsDistinct() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    ObjectMapper objectMapper = new ObjectMapper();

    when(lineage.getAssetByKey(anyString())).thenAnswer(invocation -> {
      String key = invocation.getArgument(0);
      return switch (key) {
        case "chart:analysis:11" -> asset(201L, key, LineageAssetType.CHART, null);
        case "dataset:22" -> asset(202L, key, LineageAssetType.DATASET, null);
        case "dataset-field:22:region" -> asset(203L, key, LineageAssetType.DATASET_FIELD, 202L);
        case "dataset-field:22:amount" -> asset(204L, key, LineageAssetType.DATASET_FIELD, 202L);
        default -> throw new IllegalArgumentException("missing: " + key);
      };
    });
    when(lineage.registerAsset(any())).thenAnswer(invocation -> {
      LineageService.RegisterAssetCommand command = invocation.getArgument(0);
      long id = command.assetKey().startsWith("dashboard:") ? 300L : 301L;
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
    });

    DashboardLineageService service = new DashboardLineageService(lineage, maintenance, objectMapper);
    DashboardAsset dashboard = dashboard(1L, 102L, 2, 101L, 1);
    DashboardVersion version = new DashboardVersion(
        101L, 1L, 1, "销售驾驶舱", null, null, Instant.EPOCH);
    Object inline = Map.of(
        "datasetId", "22",
        "chartType", "BAR",
        "querySpec", Map.of(
            "dimensions", List.of("region"),
            "metrics", List.of(Map.of("fieldId", "amount", "aggregation", "SUM")),
            "filters", List.of(Map.of("fieldId", "region", "operator", "EQ")),
            "sorts", List.of()));
    List<DashboardWidgetSnapshot> widgets = List.of(
        widget(1L, 101L, "w1", 11L, "区域销售", null, 1),
        widget(2L, 101L, "w2", 11L, "区域销售副本", null, 2),
        widget(3L, 101L, "inline-gmv", null, "GMV", inline, 3));

    service.syncVersion(dashboard, version, widgets, true);

    verify(maintenance).clearRelationsByEvidence(DashboardLineageService.EVIDENCE_SOURCE_TYPE, "1");

    ArgumentCaptor<LineageService.RegisterAssetCommand> assets =
        ArgumentCaptor.forClass(LineageService.RegisterAssetCommand.class);
    verify(lineage, org.mockito.Mockito.times(2)).registerAsset(assets.capture());
    LineageService.RegisterAssetCommand dashboardAsset = assets.getAllValues().stream()
        .filter(command -> command.assetType() == LineageAssetType.DASHBOARD)
        .findFirst().orElseThrow();
    LineageService.RegisterAssetCommand inlineAsset = assets.getAllValues().stream()
        .filter(command -> "chart:dashboard:1:widget:inline-gmv".equals(command.assetKey()))
        .findFirst().orElseThrow();
    assertEquals("dashboard:1", dashboardAsset.assetKey());
    assertEquals(300L, inlineAsset.parentAssetId());
    assertEquals("SUCCESS", inlineAsset.properties().path("lineageParseStatus").asText());

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage, org.mockito.Mockito.times(6)).registerRelation(relations.capture());
    long containsCount = relations.getAllValues().stream()
        .filter(relation -> relation.relationType() == LineageRelationType.CONTAINS)
        .count();
    long consumesCount = relations.getAllValues().stream()
        .filter(relation -> relation.relationType() == LineageRelationType.CONSUMES)
        .count();
    assertEquals(3L, containsCount);
    assertEquals(3L, consumesCount);

    List<String> linkedVersions = relations.getAllValues().stream()
        .filter(relation -> relation.relationType() == LineageRelationType.CONTAINS)
        .filter(relation -> "LINKED_ANALYSIS".equals(
            relation.properties().path("bindingMode").asText()))
        .map(LineageService.RegisterRelationCommand::version)
        .toList();
    assertEquals(2, linkedVersions.size());
    assertTrue(linkedVersions.stream().anyMatch(value -> value.contains(":w1:")));
    assertTrue(linkedVersions.stream().anyMatch(value -> value.contains(":w2:")));

    LineageService.RegisterRelationCommand region = relations.getAllValues().stream()
        .filter(relation -> "region".equals(relation.properties().path("fieldId").asText()))
        .findFirst().orElseThrow();
    List<String> roles = new ArrayList<>();
    region.properties().path("usageRoles").forEach(node -> roles.add(node.asText()));
    assertEquals(Set.of("DIMENSION", "FILTER"), Set.copyOf(roles));
  }

  @Test
  void unresolvedInlineStillCreatesChartContainmentWithoutInventingDataset() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    when(lineage.registerAsset(any())).thenAnswer(invocation -> {
      LineageService.RegisterAssetCommand command = invocation.getArgument(0);
      return new LineageAsset(
          command.assetType() == LineageAssetType.DASHBOARD ? 10L : 11L,
          command.assetKey(), command.assetType(), command.name(), command.sourceType(),
          command.sourceId(), command.parentAssetId(), null, null, null, null, null,
          command.properties(), Instant.EPOCH, Instant.EPOCH);
    });
    DashboardLineageService service = new DashboardLineageService(
        lineage, maintenance, new ObjectMapper());
    DashboardAsset dashboard = dashboard(1L, 101L, 1, null, 0);
    DashboardVersion version = new DashboardVersion(
        101L, 1L, 1, "D", null, null, Instant.EPOCH);

    service.syncVersion(
        dashboard,
        version,
        List.of(widget(1L, 101L, "w", null, "临时", Map.of("type", "bar"), 1)),
        false);

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage, org.mockito.Mockito.times(1)).registerRelation(relations.capture());
    assertEquals(LineageRelationType.CONTAINS, relations.getValue().relationType());
  }

  private static DashboardAsset dashboard(
      long id,
      Long currentVersionId,
      int currentVersionNo,
      Long publishedVersionId,
      int publishedVersionNo) {
    return new DashboardAsset(
        id,
        "D",
        null,
        currentVersionId,
        currentVersionNo,
        publishedVersionId,
        publishedVersionNo,
        publishedVersionId == null ? null : Instant.EPOCH,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private static DashboardWidgetSnapshot widget(
      long id,
      long versionId,
      String key,
      Long analysisId,
      String title,
      Object inline,
      int sortOrder) {
    return new DashboardWidgetSnapshot(
        id, versionId, key, analysisId, title, inline,
        0, sortOrder - 1, 8, 6, null, null, sortOrder);
  }

  private static LineageAsset asset(
      long id,
      String key,
      LineageAssetType type,
      Long parentId) {
    return new LineageAsset(
        id, key, type, key, "TEST", key, parentId,
        null, null, null, null, null, null, Instant.EPOCH, Instant.EPOCH);
  }
}
