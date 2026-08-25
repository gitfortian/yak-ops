
package io.yak.ops.business.lineage.service;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable read facade for asset lookup, search and bounded graph traversal. */
@Service
public class LineageQueryService {

  public static final int MAX_GRAPH_DEPTH = 10;
  public static final int MAX_ASSET_SEARCH_LIMIT = 100;

  private final LineageRepository repository;

  public LineageQueryService(LineageRepository repository) {
    this.repository = repository;
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageAsset getAsset(long assetId) {
    requirePositive(assetId, "assetId");
    return repository.findAsset(assetId)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + assetId));
  }

  /** Missing-by-key is a normal fallback branch for derived metadata registration. */
  @Transactional(
      value = "yakBusinessTransactionManager",
      readOnly = true,
      noRollbackFor = IllegalArgumentException.class)
  public LineageAsset getAssetByKey(String assetKey) {
    String normalized = required(assetKey, "assetKey", 512);
    return repository.findAssetByKey(normalized)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + normalized));
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit) {
    String normalizedKeyword = optional(keyword, 200);
    int actualLimit = Math.min(MAX_ASSET_SEARCH_LIMIT, Math.max(1, limit));
    return repository.searchAssets(normalizedKeyword, assetType, actualLimit);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph upstream(long assetId, int depth) {
    return graph(assetId, LineageDirection.UPSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph downstream(long assetId, int depth) {
    return graph(assetId, LineageDirection.DOWNSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph graph(long assetId, LineageDirection direction, int depth) {
    LineageAsset root = getAsset(assetId);
    LineageDirection actualDirection = direction == null ? LineageDirection.BOTH : direction;
    if (depth < 1 || depth > MAX_GRAPH_DEPTH) {
      throw new IllegalArgumentException("depth 必须在 1 到 " + MAX_GRAPH_DEPTH + " 之间");
    }

    Map<Long, LineageAsset> nodes = new LinkedHashMap<>();
    Map<Long, LineageRelation> relations = new LinkedHashMap<>();
    nodes.put(root.id(), root);

    Set<Long> visited = new LinkedHashSet<>();
    visited.add(root.id());
    Set<Long> frontier = new LinkedHashSet<>();
    frontier.add(root.id());

    for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
      List<LineageRelation> discoveredRelations = new ArrayList<>();
      if (actualDirection != LineageDirection.UPSTREAM) {
        discoveredRelations.addAll(repository.findOutgoingRelations(frontier));
      }
      if (actualDirection != LineageDirection.DOWNSTREAM) {
        discoveredRelations.addAll(repository.findIncomingRelations(frontier));
      }

      Set<Long> endpointIds = new LinkedHashSet<>();
      Set<Long> nextFrontier = new LinkedHashSet<>();
      for (LineageRelation relation : discoveredRelations) {
        relations.putIfAbsent(relation.id(), relation);
        endpointIds.add(relation.sourceAssetId());
        endpointIds.add(relation.targetAssetId());
        if (!visited.contains(relation.sourceAssetId())) nextFrontier.add(relation.sourceAssetId());
        if (!visited.contains(relation.targetAssetId())) nextFrontier.add(relation.targetAssetId());
      }

      repository.findAssetsByIds(endpointIds)
          .forEach(asset -> nodes.putIfAbsent(asset.id(), asset));
      nextFrontier.removeAll(visited);
      visited.addAll(nextFrontier);
      frontier = nextFrontier;
    }

    return new LineageGraph(
        root,
        actualDirection,
        depth,
        List.copyOf(nodes.values()),
        List.copyOf(relations.values()));
  }

  private static long requirePositive(long value, String field) {
    if (value <= 0) throw new IllegalArgumentException(field + " 必须大于 0");
    return value;
  }

  private static String required(String value, String field, int maxLength) {
    String normalized = optional(value, maxLength);
    if (normalized == null) throw new IllegalArgumentException(field + " 不能为空");
    return normalized;
  }

  private static String optional(String value, int maxLength) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("字段长度不能超过 " + maxLength);
    }
    return normalized;
  }
}
