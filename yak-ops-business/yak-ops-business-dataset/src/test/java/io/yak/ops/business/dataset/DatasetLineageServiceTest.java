package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import io.yak.ops.business.lineage.SqlProjectionLineageAnalyzer;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetLineageServiceTest {

  @Test
  void queryRevisionUsesFrozenRevisionAndConnectsPhysicalColumnsToStableDatasetFields() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    SqlProjectionLineageAnalyzer analyzer = mock(SqlProjectionLineageAnalyzer.class);
    Map<String, LineageAsset> assets = stubAssets(lineage);

    DatasetLineageService service = new DatasetLineageService(
        lineage, maintenance, catalog, new ObjectMapper());
    service.setProjectionAnalyzer(analyzer);

    TaskAsset taskAsset = new TaskAsset(
        11L,
        TaskAssetSource.DATA_DEVELOPMENT,
        "101",
        7L,
        "sales.sql",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(11L, 99L, 5),
        Instant.EPOCH,
        Instant.EPOCH);
    TaskSourceRevision frozenRevision = new TaskSourceRevision(
        71L,
        3,
        new TaskDefinition(
            "SQL",
            1,
            "SELECT o.id AS order_id, o.amount FROM ods.orders o",
            "{\"dataSourceId\":\"12\"}"),
        "checksum-71");
    when(catalog.resolveRevision(11L, 71L)).thenReturn(
        new TaskAssetRevision(taskAsset, frozenRevision));

    SqlProjectionLineageAnalyzer.TableRef orders = table("ods.orders", "ods", "orders");
    when(analyzer.analyze(anyString(), any())).thenReturn(
        new SqlProjectionLineageAnalyzer.ProjectionResult(
            List.of(
                mapping(orders, "id", "order_id", 1),
                mapping(orders, "amount", "amount", 2)),
            2,
            0));

    DatasetDetail detail = queryRevisionDataset();
    service.syncCurrent(detail);

    verify(catalog).resolveRevision(11L, 71L);
    verify(maintenance).clearRelationsByEvidence(
        DatasetLineageService.EVIDENCE_SOURCE_TYPE, "21");

    LineageAsset dataset = assets.get("dataset:21");
    LineageAsset orderField = assets.get("dataset-field:21:field-order");
    LineageAsset amountField = assets.get("dataset-field:21:field-amount");
    assertNotNull(dataset);
    assertNotNull(orderField);
    assertNotNull(amountField);
    assertEquals(dataset.id(), orderField.parentAssetId());
    assertEquals("71", dataset.properties().path("sourceTaskRevisionId").asText());
    assertEquals("SUCCESS", dataset.properties().path("lineageParseStatus").asText());

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage, org.mockito.Mockito.times(6)).registerRelation(relations.capture());
    assertEquals(2, count(relations, LineageRelationType.CONTAINS));
    assertEquals(4, count(relations, LineageRelationType.DERIVES_FROM));

    assertTrue(relations.getAllValues().stream().anyMatch(relation ->
        key(assets, relation.sourceAssetId()).equals("dataset-field:21:field-order")
            && key(assets, relation.targetAssetId()).equals("dataset:21")
            && relation.relationType() == LineageRelationType.CONTAINS));
    assertTrue(relations.getAllValues().stream().anyMatch(relation ->
        key(assets, relation.sourceAssetId()).equals("column:12:ods.orders.id")
            && key(assets, relation.targetAssetId()).equals("dataset-field:21:field-order")
            && relation.relationType() == LineageRelationType.DERIVES_FROM));
    assertTrue(assets.containsKey("sql-task:data-development:101"));
  }

  @Test
  void standaloneSqlDatasetBuildsLineageWithoutTaskCatalog() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    SqlProjectionLineageAnalyzer analyzer = mock(SqlProjectionLineageAnalyzer.class);
    Map<String, LineageAsset> assets = stubAssets(lineage);

    DatasetLineageService service = new DatasetLineageService(
        lineage, maintenance, catalog, new ObjectMapper());
    service.setProjectionAnalyzer(analyzer);

    SqlProjectionLineageAnalyzer.TableRef orders = table("ods.orders", "ods", "orders");
    when(analyzer.analyze(anyString(), any())).thenReturn(
        new SqlProjectionLineageAnalyzer.ProjectionResult(
            List.of(mapping(orders, "id", "order_id", 1)),
            1,
            0));

    Dataset dataset = new Dataset(
        22L, "standalone", null, DatasetStatus.ONLINE, 41L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        41L,
        22L,
        1,
        DatasetSourceType.SQL_QUERY,
        0L,
        0L,
        0,
        "12",
        "SELECT o.id AS order_id FROM ods.orders o",
        "[]",
        Instant.EPOCH);
    DatasetField field = new DatasetField(
        "field-order",
        41L,
        "order_id",
        "order_id",
        DatasetFieldDataType.NUMBER,
        false,
        null,
        DatasetFieldRole.DIMENSION,
        1);

    service.syncCurrent(new DatasetDetail(dataset, version, List.of(version), List.of(field)));

    verifyNoInteractions(catalog);
    assertTrue(assets.containsKey("dataset:22"));
    assertTrue(assets.containsKey("column:12:ods.orders.id"));
    assertTrue(assets.keySet().stream().noneMatch(key -> key.startsWith("sql-task:")));

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage, org.mockito.Mockito.times(3)).registerRelation(relations.capture());
    assertEquals(1, count(relations, LineageRelationType.CONTAINS));
    assertEquals(2, count(relations, LineageRelationType.DERIVES_FROM));
  }

  @Test
  void analyzerFailureReplacesStaleFactsButKeepsDatasetStructure() {
    LineageService lineage = mock(LineageService.class);
    LineageMaintenanceService maintenance = mock(LineageMaintenanceService.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    SqlProjectionLineageAnalyzer analyzer = mock(SqlProjectionLineageAnalyzer.class);
    Map<String, LineageAsset> assets = stubAssets(lineage);

    DatasetLineageService service = new DatasetLineageService(
        lineage, maintenance, catalog, new ObjectMapper());
    service.setProjectionAnalyzer(analyzer);
    when(analyzer.analyze(anyString(), any())).thenThrow(new IllegalStateException("parse failed"));

    Dataset dataset = new Dataset(
        23L, "broken", null, DatasetStatus.ONLINE, 42L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        42L,
        23L,
        1,
        DatasetSourceType.SQL_QUERY,
        0L,
        0L,
        0,
        "12",
        "SELECT broken",
        "[]",
        Instant.EPOCH);
    DatasetField field = new DatasetField(
        "field-one",
        42L,
        "one",
        "one",
        DatasetFieldDataType.NUMBER,
        true,
        null,
        DatasetFieldRole.DIMENSION,
        1);

    service.syncCurrent(new DatasetDetail(dataset, version, List.of(version), List.of(field)));

    verify(maintenance).clearRelationsByEvidence(
        DatasetLineageService.EVIDENCE_SOURCE_TYPE, "23");
    assertEquals("FAILED", assets.get("dataset:23").properties().path("lineageParseStatus").asText());

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineage).registerRelation(relations.capture());
    assertEquals(LineageRelationType.CONTAINS, relations.getValue().relationType());
  }

  private static DatasetDetail queryRevisionDataset() {
    Dataset dataset = new Dataset(
        21L, "sales", "sales dataset", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        31L,
        21L,
        1,
        DatasetSourceType.QUERY_REVISION,
        11L,
        71L,
        3,
        "[]",
        Instant.EPOCH);
    DatasetField orderField = new DatasetField(
        "field-order",
        31L,
        "order_id",
        "订单 ID",
        DatasetFieldDataType.NUMBER,
        false,
        null,
        DatasetFieldRole.DIMENSION,
        1);
    DatasetField amountField = new DatasetField(
        "field-amount",
        31L,
        "amount",
        "销售额",
        DatasetFieldDataType.NUMBER,
        true,
        null,
        DatasetFieldRole.MEASURE,
        2);
    return new DatasetDetail(
        dataset, version, List.of(version), List.of(orderField, amountField));
  }

  private static SqlProjectionLineageAnalyzer.TableRef table(
      String qualifiedName,
      String schema,
      String table) {
    return new SqlProjectionLineageAnalyzer.TableRef(
        qualifiedName,
        qualifiedName,
        null,
        schema,
        table);
  }

  private static SqlProjectionLineageAnalyzer.ProjectionMapping mapping(
      SqlProjectionLineageAnalyzer.TableRef sourceTable,
      String sourceColumn,
      String outputColumn,
      int outputOrdinal) {
    return new SqlProjectionLineageAnalyzer.ProjectionMapping(
        sourceTable,
        sourceColumn,
        outputColumn,
        SqlProjectionLineageAnalyzer.MappingKind.IDENTITY,
        sourceTable.tableName() + "." + sourceColumn,
        outputOrdinal,
        1);
  }

  private static long count(
      ArgumentCaptor<LineageService.RegisterRelationCommand> relations,
      LineageRelationType type) {
    return relations.getAllValues().stream()
        .filter(value -> value.relationType() == type)
        .count();
  }

  private static Map<String, LineageAsset> stubAssets(LineageService lineage) {
    AtomicLong ids = new AtomicLong(1);
    Map<String, LineageAsset> assets = new LinkedHashMap<>();
    when(lineage.registerAsset(any())).thenAnswer(invocation -> {
      LineageService.RegisterAssetCommand command = invocation.getArgument(0);
      LineageAsset existing = assets.get(command.assetKey());
      long id = existing == null ? ids.getAndIncrement() : existing.id();
      LineageAsset asset = new LineageAsset(
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
      assets.put(command.assetKey(), asset);
      return asset;
    });
    when(lineage.getAssetByKey(anyString())).thenAnswer(invocation -> {
      String key = invocation.getArgument(0);
      LineageAsset asset = assets.get(key);
      if (asset == null) throw new IllegalArgumentException("missing: " + key);
      return asset;
    });
    return assets;
  }

  private static String key(Map<String, LineageAsset> assets, long id) {
    return assets.values().stream()
        .filter(asset -> asset.id() == id)
        .map(LineageAsset::assetKey)
        .findFirst()
        .orElse("<missing:" + id + ">");
  }
}
