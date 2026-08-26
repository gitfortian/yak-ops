package io.yak.ops.boot.home;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.query.LineageQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 首页数据资产与血缘只读聚合。 */
@Service
public class HomeAssetOverviewService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HomeAssetOverviewService.class);
  private static final int DATASET_LIST_LIMIT = 5;
  private static final int LINEAGE_RELATION_LIMIT = 6;
  private static final int LINEAGE_ACTIVITY_LIMIT = 3;

  private final ObjectProvider<DatasetService> datasetServiceProvider;
  private final ObjectProvider<LineageQueryService> lineageQueryServiceProvider;

  public HomeAssetOverviewService(
      ObjectProvider<DatasetService> datasetServiceProvider,
      ObjectProvider<LineageQueryService> lineageQueryServiceProvider) {
    this.datasetServiceProvider = datasetServiceProvider;
    this.lineageQueryServiceProvider = lineageQueryServiceProvider;
  }

  public OverviewResponse overview() {
    DayRange today = DayRange.today();
    List<Dataset> datasets = loadDatasets();
    LineageQueryService.Overview lineage = loadLineage(today);
    return new OverviewResponse(datasetOverview(datasets, lineage, today), lineageOverview(lineage));
  }

  private List<Dataset> loadDatasets() {
    DatasetService service = datasetServiceProvider.getIfAvailable();
    if (service == null) return null;
    try {
      return service.list();
    } catch (RuntimeException exception) {
      LOGGER.warn("加载首页数据集总览失败", exception);
      return null;
    }
  }

  private LineageQueryService.Overview loadLineage(DayRange today) {
    LineageQueryService service = lineageQueryServiceProvider.getIfAvailable();
    if (service == null) return null;
    try {
      return service.overview(today.start(), today.end(), LINEAGE_RELATION_LIMIT);
    } catch (RuntimeException exception) {
      LOGGER.warn("加载首页血缘总览失败", exception);
      return null;
    }
  }

  private DatasetOverview datasetOverview(
      List<Dataset> datasets, LineageQueryService.Overview lineage, DayRange today) {
    Long datasetCount = datasets == null ? null : (long) datasets.size();
    Long todayCreatedCount =
        datasets == null
            ? null
            : datasets.stream()
                .map(Dataset::createTime)
                .filter(value -> inRange(value, today))
                .count();
    Long tableAssetCount = lineage == null ? null : lineage.tableAssetCount();
    Long columnAssetCount = lineage == null ? null : lineage.columnAssetCount();

    return new DatasetOverview(
        datasetCount,
        tableAssetCount,
        columnAssetCount,
        todayCreatedCount,
        recentDatasets(datasets),
        onlineDatasets(datasets));
  }

  private List<DatasetItem> recentDatasets(List<Dataset> datasets) {
    if (datasets == null || datasets.isEmpty()) return List.of();
    return datasets.stream()
        .sorted(datasetUpdatedComparator())
        .limit(DATASET_LIST_LIMIT)
        .map(this::datasetItem)
        .toList();
  }

  private List<DatasetItem> onlineDatasets(List<Dataset> datasets) {
    if (datasets == null || datasets.isEmpty()) return List.of();
    return datasets.stream()
        .filter(dataset -> dataset.status() == DatasetStatus.ONLINE)
        .sorted(datasetUpdatedComparator())
        .limit(DATASET_LIST_LIMIT)
        .map(this::datasetItem)
        .toList();
  }

  private Comparator<Dataset> datasetUpdatedComparator() {
    return Comparator.comparing(
            this::datasetUpdatedAt,
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Dataset::id, Comparator.reverseOrder());
  }

  private Instant datasetUpdatedAt(Dataset dataset) {
    return dataset.updateTime() == null ? dataset.createTime() : dataset.updateTime();
  }

  private DatasetItem datasetItem(Dataset dataset) {
    return new DatasetItem(
        String.valueOf(dataset.id()),
        defaultText(dataset.name(), "未命名数据集"),
        dataset.description(),
        dataset.status() == null ? "UNKNOWN" : dataset.status().name(),
        toText(datasetUpdatedAt(dataset)));
  }

  private LineageOverview lineageOverview(LineageQueryService.Overview lineage) {
    if (lineage == null) {
      return new LineageOverview(null, null, null, null, List.of(), List.of(), List.of());
    }

    Map<Long, LineageAsset> assetsById = new LinkedHashMap<>();
    for (LineageAsset asset : lineage.nodes()) {
      if (asset != null) assetsById.put(asset.id(), asset);
    }

    List<LineageNode> nodes =
        assetsById.values().stream()
            .map(
                asset ->
                    new LineageNode(
                        String.valueOf(asset.id()),
                        assetName(asset),
                        asset.assetType().name(),
                        asset.sourceType()))
            .toList();

    List<LineageEdge> edges = new ArrayList<>();
    List<LineageActivity> activities = new ArrayList<>();
    for (LineageRelation relation : lineage.relations()) {
      LineageAsset source = assetsById.get(relation.sourceAssetId());
      LineageAsset target = assetsById.get(relation.targetAssetId());
      if (source == null || target == null) continue;

      String relationId = String.valueOf(relation.id());
      String relationType = relation.relationType().name();
      edges.add(
          new LineageEdge(
              relationId,
              String.valueOf(source.id()),
              String.valueOf(target.id()),
              relationType));
      if (activities.size() < LINEAGE_ACTIVITY_LIMIT) {
        activities.add(
            new LineageActivity(
                relationId,
                assetName(source),
                assetName(target),
                relationType,
                toText(relationOccurredAt(relation))));
      }
    }

    return new LineageOverview(
        lineage.assetCount(),
        lineage.relationCount(),
        lineage.updatedAssetCount(),
        lineage.datasetAssetCount(),
        List.copyOf(nodes),
        List.copyOf(edges),
        List.copyOf(activities));
  }

  private Instant relationOccurredAt(LineageRelation relation) {
    if (relation.updateTime() != null) return relation.updateTime();
    if (relation.observedAt() != null) return relation.observedAt();
    return relation.createTime();
  }

  private String assetName(LineageAsset asset) {
    return firstText(
        asset.name(),
        asset.tableName(),
        asset.columnName(),
        asset.assetKey(),
        "未命名资产");
  }

  private boolean inRange(Instant value, DayRange range) {
    return value != null && !value.isBefore(range.start()) && value.isBefore(range.end());
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private static String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String toText(Instant value) {
    return value == null ? null : value.toString();
  }

  public record OverviewResponse(DatasetOverview dataset, LineageOverview lineage) {
  }

  public record DatasetOverview(
      Long datasetCount,
      Long tableAssetCount,
      Long columnAssetCount,
      Long todayCreatedCount,
      List<DatasetItem> recentDatasets,
      List<DatasetItem> onlineDatasets) {
  }

  public record DatasetItem(
      String id,
      String name,
      String description,
      String status,
      String updatedAt) {
  }

  public record LineageOverview(
      Long assetCount,
      Long relationCount,
      Long todayUpdatedCount,
      Long datasetAssetCount,
      List<LineageNode> nodes,
      List<LineageEdge> edges,
      List<LineageActivity> recentActivities) {
  }

  public record LineageNode(String id, String name, String assetType, String sourceType) {
  }

  public record LineageEdge(
      String id, String sourceAssetId, String targetAssetId, String relationType) {
  }

  public record LineageActivity(
      String id,
      String sourceName,
      String targetName,
      String relationType,
      String occurredAt) {
  }

  private record DayRange(Instant start, Instant end) {

    static DayRange today() {
      ZoneId zone = ZoneId.systemDefault();
      LocalDate today = LocalDate.now(zone);
      Instant start = today.atStartOfDay(zone).toInstant();
      Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
      return new DayRange(start, end);
    }
  }
}
