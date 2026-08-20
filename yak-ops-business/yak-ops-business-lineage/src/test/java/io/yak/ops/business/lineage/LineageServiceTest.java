package io.yak.ops.business.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LineageServiceTest {

  @Test
  void traversesMultiHopGraphAndStopsAtVisitedAssets() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageService service = new LineageService(repository);

    LineageAsset tableA = service.registerAsset(asset("table:a", LineageAssetType.TABLE));
    LineageAsset taskB = service.registerAsset(asset("sql:b", LineageAssetType.SQL_TASK));
    LineageAsset tableC = service.registerAsset(asset("table:c", LineageAssetType.TABLE));
    LineageAsset datasetD = service.registerAsset(asset("dataset:d", LineageAssetType.DATASET));
    LineageAsset chartE = service.registerAsset(asset("chart:e", LineageAssetType.CHART));

    service.registerRelation(relation(tableA.id(), taskB.id(), LineageRelationType.READS_FROM));
    service.registerRelation(relation(taskB.id(), tableC.id(), LineageRelationType.WRITES_TO));
    service.registerRelation(relation(tableC.id(), datasetD.id(), LineageRelationType.DERIVES_FROM));
    service.registerRelation(relation(datasetD.id(), chartE.id(), LineageRelationType.CONSUMES));
    service.registerRelation(relation(chartE.id(), taskB.id(), LineageRelationType.CONSUMES));

    LineageGraph downstream = service.downstream(tableA.id(), 10);
    assertEquals(
        Set.of(tableA.id(), taskB.id(), tableC.id(), datasetD.id(), chartE.id()),
        ids(downstream));
    assertEquals(5, downstream.relations().size());

    LineageGraph twoHops = service.downstream(tableA.id(), 2);
    assertEquals(Set.of(tableA.id(), taskB.id(), tableC.id()), ids(twoHops));
    assertEquals(2, twoHops.relations().size());

    LineageGraph upstream = service.upstream(chartE.id(), 4);
    assertTrue(ids(upstream).contains(tableA.id()));
    assertTrue(ids(upstream).contains(datasetD.id()));
  }

  @Test
  void searchesAssetsByKeywordTypeAndLimit() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageService service = new LineageService(repository);

    service.registerAsset(new LineageService.RegisterAssetCommand(
        "dataset:1", LineageAssetType.DATASET, "Sales Dataset", "TEST", "1", null,
        null, null, null, null, null, null));
    service.registerAsset(new LineageService.RegisterAssetCommand(
        "dataset:2", LineageAssetType.DATASET, "Inventory", "TEST", "2", null,
        null, null, null, null, null, null));
    service.registerAsset(new LineageService.RegisterAssetCommand(
        "chart:1", LineageAssetType.CHART, "Sales Chart", "TEST", "3", null,
        null, null, null, null, null, null));

    List<LineageAsset> values = service.searchAssets("sales", LineageAssetType.DATASET, 10);

    assertEquals(1, values.size());
    assertEquals("dataset:1", values.get(0).assetKey());
    assertEquals(1, service.searchAssets(null, LineageAssetType.DATASET, 1).size());
  }

  @Test
  void batchSizingMakesThousandColumnLineageWritesBounded() {
    // 1,000 unique edges with two unique endpoints each: asset batch + identity read + edge batch.
    assertEquals(25, 2 * JdbcLineageRepository.batchExecutionCount(2_000, 200)
        + JdbcLineageRepository.batchExecutionCount(1_000, 200));
    assertEquals(5, JdbcLineageRepository.batchExecutionCount(1_000, 256));
    assertEquals(0, JdbcLineageRepository.batchExecutionCount(0, 200));
  }

  @Test
  void batchApiDeduplicatesInputsAndSkipsEmptyCollections() {
    InMemoryLineageRepository repository = new InMemoryLineageRepository();
    LineageService service = new LineageService(repository);

    Map<String, LineageAsset> assets = service.registerAssetsBatch(
        List.of(asset("column:a", LineageAssetType.COLUMN),
            asset("column:a", LineageAssetType.COLUMN),
            asset("column:b", LineageAssetType.COLUMN)), 1);
    service.registerRelationsBatch(List.of(
        relation(assets.get("column:a").id(), assets.get("column:b").id(),
            LineageRelationType.DERIVES_FROM),
        relation(assets.get("column:a").id(), assets.get("column:b").id(),
            LineageRelationType.DERIVES_FROM)), 7);

    assertEquals(2, assets.size());
    assertEquals(1, repository.relations.size());
    assertTrue(service.registerAssetsBatch(List.of(), 10).isEmpty());
    service.registerRelationsBatch(List.of(), 10);
    assertEquals(1, repository.relations.size());
  }

  private static Set<Long> ids(LineageGraph graph) {
    return graph.nodes().stream().map(LineageAsset::id).collect(Collectors.toSet());
  }

  private static LineageService.RegisterAssetCommand asset(
      String assetKey, LineageAssetType assetType) {
    return new LineageService.RegisterAssetCommand(
        assetKey, assetType, assetKey, "TEST", assetKey, null,
        null, null, null, null, null, null);
  }

  private static LineageService.RegisterRelationCommand relation(
      long sourceAssetId, long targetAssetId, LineageRelationType relationType) {
    return new LineageService.RegisterRelationCommand(
        sourceAssetId, targetAssetId, relationType, "TEST", "case", null,
        null, "v1", Instant.parse("2026-08-20T00:00:00Z"), null);
  }

  private static final class InMemoryLineageRepository implements LineageRepository {
    private final AtomicLong assetIds = new AtomicLong(1);
    private final AtomicLong relationIds = new AtomicLong(1);
    private final Map<Long, LineageAsset> assets = new LinkedHashMap<>();
    private final Map<String, Long> assetKeys = new LinkedHashMap<>();
    private final Map<Long, LineageRelation> relations = new LinkedHashMap<>();

    @Override
    public LineageAsset upsertAsset(AssetWrite write) {
      Long existingId = assetKeys.get(write.assetKey());
      long id = existingId == null ? assetIds.getAndIncrement() : existingId;
      Instant now = Instant.parse("2026-08-20T00:00:00Z");
      LineageAsset existing = assets.get(id);
      LineageAsset asset = new LineageAsset(
          id, write.assetKey(), write.assetType(), write.name(), write.sourceType(), write.sourceId(),
          write.parentAssetId(), write.dataSourceId(), write.databaseName(), write.schemaName(),
          write.tableName(), write.columnName(), write.properties(),
          existing == null ? now : existing.createTime(), now);
      assets.put(id, asset);
      assetKeys.put(write.assetKey(), id);
      return asset;
    }

    @Override
    public LineageRelation upsertRelation(RelationWrite write) {
      long id = relationIds.getAndIncrement();
      Instant now = Instant.parse("2026-08-20T00:00:00Z");
      LineageRelation relation = new LineageRelation(
          id, write.sourceAssetId(), write.targetAssetId(), write.relationType(),
          write.sourceType(), write.sourceId(), write.expression(), write.confidence(),
          write.version(), write.observedAt(), write.properties(), now, now);
      relations.put(id, relation);
      return relation;
    }

    @Override
    public Map<String, LineageAsset> upsertAssets(List<AssetWrite> writes, int batchSize) {
      Map<String, LineageAsset> result = new LinkedHashMap<>();
      for (AssetWrite write : writes) result.put(write.assetKey(), upsertAsset(write));
      return result;
    }

    @Override
    public void upsertRelations(List<RelationWrite> writes, int batchSize) {
      for (RelationWrite write : writes) upsertRelation(write);
    }

    @Override
    public Optional<LineageAsset> findAsset(long assetId) {
      return Optional.ofNullable(assets.get(assetId));
    }

    @Override
    public Optional<LineageAsset> findAssetByKey(String assetKey) {
      Long id = assetKeys.get(assetKey);
      return id == null ? Optional.empty() : findAsset(id);
    }

    @Override
    public List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit) {
      String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
      return assets.values().stream()
          .filter(asset -> assetType == null || asset.assetType() == assetType)
          .filter(asset -> normalized.isBlank()
              || asset.name().toLowerCase(Locale.ROOT).contains(normalized)
              || asset.assetKey().toLowerCase(Locale.ROOT).contains(normalized))
          .limit(limit)
          .toList();
    }

    @Override
    public List<LineageAsset> findAssetsByIds(Set<Long> assetIds) {
      return assetIds.stream().map(assets::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds) {
      return relations.values().stream()
          .filter(relation -> sourceAssetIds.contains(relation.sourceAssetId()))
          .toList();
    }

    @Override
    public List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds) {
      return relations.values().stream()
          .filter(relation -> targetAssetIds.contains(relation.targetAssetId()))
          .toList();
    }
  }
}
