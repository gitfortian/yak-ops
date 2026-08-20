package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core service for asset registration, typed relations and bounded multi-hop traversal. */
@Service
public class LineageService {

  public static final int MAX_GRAPH_DEPTH = 10;

  private final LineageRepository repository;

  public LineageService(LineageRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public LineageAsset registerAsset(RegisterAssetCommand command) {
    Objects.requireNonNull(command, "command");
    String assetKey = required(command.assetKey(), "assetKey", 512);
    LineageAssetType assetType = Objects.requireNonNull(command.assetType(), "assetType");
    String name = optional(command.name(), 200);
    if (name == null) name = assetKey;

    Long parentAssetId = command.parentAssetId();
    if (parentAssetId != null) {
      requirePositive(parentAssetId, "parentAssetId");
      getAsset(parentAssetId);
    }

    return repository.upsertAsset(new LineageRepository.AssetWrite(
        assetKey,
        assetType,
        name,
        valueOrEmpty(command.sourceType(), 64),
        valueOrEmpty(command.sourceId(), 200),
        parentAssetId,
        optional(command.dataSourceId(), 64),
        optional(command.databaseName(), 256),
        optional(command.schemaName(), 256),
        optional(command.tableName(), 256),
        optional(command.columnName(), 256),
        command.properties()));
  }

  @Transactional
  public LineageRelation registerRelation(RegisterRelationCommand command) {
    Objects.requireNonNull(command, "command");
    requirePositive(command.sourceAssetId(), "sourceAssetId");
    requirePositive(command.targetAssetId(), "targetAssetId");
    if (command.sourceAssetId() == command.targetAssetId()) {
      throw new IllegalArgumentException("血缘关系不能指向资产自身");
    }
    getAsset(command.sourceAssetId());
    getAsset(command.targetAssetId());

    LineageRelationType relationType =
        Objects.requireNonNull(command.relationType(), "relationType");
    BigDecimal confidence = command.confidence() == null ? BigDecimal.ONE : command.confidence();
    if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
    }

    return repository.upsertRelation(new LineageRepository.RelationWrite(
        command.sourceAssetId(),
        command.targetAssetId(),
        relationType,
        valueOrEmpty(command.sourceType(), 64),
        valueOrEmpty(command.sourceId(), 200),
        optional(command.expression(), 16000),
        confidence,
        valueOrEmpty(command.version(), 128),
        command.observedAt() == null ? Instant.now() : command.observedAt(),
        command.properties()));
  }

  @Transactional(readOnly = true)
  public LineageAsset getAsset(long assetId) {
    requirePositive(assetId, "assetId");
    return repository.findAsset(assetId)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + assetId));
  }

  @Transactional(readOnly = true)
  public LineageAsset getAssetByKey(String assetKey) {
    String normalized = required(assetKey, "assetKey", 512);
    return repository.findAssetByKey(normalized)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + normalized));
  }

  @Transactional(readOnly = true)
  public LineageGraph upstream(long assetId, int depth) {
    return graph(assetId, LineageDirection.UPSTREAM, depth);
  }

  @Transactional(readOnly = true)
  public LineageGraph downstream(long assetId, int depth) {
    return graph(assetId, LineageDirection.DOWNSTREAM, depth);
  }

  @Transactional(readOnly = true)
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

      for (LineageAsset asset : repository.findAssetsByIds(endpointIds)) {
        nodes.putIfAbsent(asset.id(), asset);
      }
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

  private static String valueOrEmpty(String value, int maxLength) {
    String normalized = optional(value, maxLength);
    return normalized == null ? "" : normalized;
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

  public record RegisterAssetCommand(
      String assetKey,
      LineageAssetType assetType,
      String name,
      String sourceType,
      String sourceId,
      Long parentAssetId,
      String dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      String columnName,
      JsonNode properties) {
  }

  public record RegisterRelationCommand(
      long sourceAssetId,
      long targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties) {
  }
}
