
package io.yak.ops.business.lineage.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable application facade for registering lineage assets and relations. */
@Service
public class LineageWriteService {

  private final LineageRepository repository;

  public LineageWriteService(LineageRepository repository) {
    this.repository = repository;
  }

  @Transactional("yakBusinessTransactionManager")
  public LineageAsset registerAsset(RegisterAssetCommand command) {
    return repository.upsertAsset(toAssetDraft(command, true));
  }

  @Transactional("yakBusinessTransactionManager")
  public LineageRelation registerRelation(RegisterRelationCommand command) {
    return repository.upsertRelation(toRelationDraft(command, true));
  }

  /** Registers deduplicated assets in bounded persistence batches. */
  @Transactional("yakBusinessTransactionManager")
  public Map<String, LineageAsset> registerAssetsBatch(
      List<RegisterAssetCommand> commands, int batchSize) {
    requireBatchSize(batchSize);
    if (commands == null || commands.isEmpty()) return Map.of();
    Map<String, LineageAssetDraft> drafts = new LinkedHashMap<>();
    for (RegisterAssetCommand command : commands) {
      LineageAssetDraft draft = toAssetDraft(command, false);
      drafts.putIfAbsent(draft.assetKey(), draft);
    }
    return repository.upsertAssets(List.copyOf(drafts.values()), batchSize);
  }

  /** Registers deduplicated relations in bounded persistence batches. */
  @Transactional("yakBusinessTransactionManager")
  public void registerRelationsBatch(List<RegisterRelationCommand> commands, int batchSize) {
    requireBatchSize(batchSize);
    if (commands == null || commands.isEmpty()) return;
    Map<String, LineageRelationDraft> drafts = new LinkedHashMap<>();
    for (RegisterRelationCommand command : commands) {
      LineageRelationDraft draft = toRelationDraft(command, false);
      String identity = draft.sourceAssetId() + "\u0000" + draft.targetAssetId() + "\u0000"
          + draft.relationType() + "\u0000" + draft.sourceType() + "\u0000"
          + draft.sourceId() + "\u0000" + draft.version();
      drafts.putIfAbsent(identity, draft);
    }
    repository.upsertRelations(List.copyOf(drafts.values()), batchSize);
  }

  private LineageAssetDraft toAssetDraft(RegisterAssetCommand command, boolean validateParent) {
    Objects.requireNonNull(command, "command");
    String assetKey = required(command.assetKey(), "assetKey", 512);
    LineageAssetType assetType = Objects.requireNonNull(command.assetType(), "assetType");
    String name = optional(command.name(), 200);
    if (name == null) name = assetKey;
    Long parentAssetId = command.parentAssetId();
    if (parentAssetId != null) {
      requirePositive(parentAssetId, "parentAssetId");
      if (validateParent) requireAsset(parentAssetId);
    }
    return new LineageAssetDraft(
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
        command.properties());
  }

  private LineageRelationDraft toRelationDraft(
      RegisterRelationCommand command, boolean validateAssets) {
    Objects.requireNonNull(command, "command");
    requirePositive(command.sourceAssetId(), "sourceAssetId");
    requirePositive(command.targetAssetId(), "targetAssetId");
    if (command.sourceAssetId() == command.targetAssetId()) {
      throw new IllegalArgumentException("血缘关系不能指向资产自身");
    }
    if (validateAssets) {
      requireAsset(command.sourceAssetId());
      requireAsset(command.targetAssetId());
    }
    LineageRelationType type = Objects.requireNonNull(command.relationType(), "relationType");
    BigDecimal confidence = command.confidence() == null ? BigDecimal.ONE : command.confidence();
    if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
    }
    return new LineageRelationDraft(
        command.sourceAssetId(),
        command.targetAssetId(),
        type,
        valueOrEmpty(command.sourceType(), 64),
        valueOrEmpty(command.sourceId(), 200),
        optional(command.expression(), 16000),
        confidence,
        valueOrEmpty(command.version(), 128),
        command.observedAt() == null ? Instant.now() : command.observedAt(),
        command.properties());
  }

  private LineageAsset requireAsset(long assetId) {
    requirePositive(assetId, "assetId");
    return repository.findAsset(assetId)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + assetId));
  }

  private static void requireBatchSize(int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("batchSize 必须大于 0");
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
