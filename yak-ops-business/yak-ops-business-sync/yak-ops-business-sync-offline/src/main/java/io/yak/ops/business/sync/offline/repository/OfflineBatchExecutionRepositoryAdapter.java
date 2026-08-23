package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.compat.LegacyOfflineExecutionCompatibilityMapper;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** BatchExecution 与持久化模型之间的适配器。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineBatchExecutionRepositoryAdapter implements OfflineBatchExecutionRepository {

  private static final List<String> OCCUPYING_STATUSES =
      Arrays.stream(BatchStatus.values())
          .filter(BatchStatus::occupiesTaskExecutionSlot)
          .map(Enum::name)
          .toList();

  private final OfflineBatchExecutionDao dao;
  private final OfflineJobExecutionRepository executionRepository;

  @Override
  public Optional<BatchExecution> findById(Long id) {
    return Optional.ofNullable(toDomain(dao.selectById(id)));
  }

  @Override
  public Optional<BatchExecution> findByTaskIdAndBatchKey(long taskId, BatchKey batchKey) {
    if (taskId <= 0L) throw new IllegalArgumentException("TaskId 必须大于 0");
    Objects.requireNonNull(batchKey, "BatchKey 不能为空");
    return Optional.ofNullable(toDomain(dao.selectByTaskIdAndBatchKey(taskId, batchKey.value())));
  }

  @Override
  public boolean hasOccupyingBatch(long taskId) {
    if (taskId <= 0L) throw new IllegalArgumentException("TaskId 必须大于 0");
    return dao.existsByTaskIdAndStatuses(taskId, OCCUPYING_STATUSES);
  }

  @Override
  public Optional<BatchExecution> findLatestOccupyingByTaskId(long taskId) {
    if (taskId <= 0L) throw new IllegalArgumentException("TaskId 必须大于 0");
    return Optional.ofNullable(toDomain(dao.selectLatestByTaskIdAndStatuses(taskId, OCCUPYING_STATUSES)));
  }

  @Override
  public List<BatchExecution> findPendingBackfills(int limit) {
    return dao.selectPendingBackfills(Math.max(1, limit)).stream().map(this::toDomain).toList();
  }

  @Override
  public boolean reservePendingBackfill(long batchId) {
    if (batchId <= 0L) throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    return dao.reservePendingBackfill(batchId, LocalDateTime.now());
  }

  @Override
  public BatchExecution insert(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    if (batch.id() != null) throw new IllegalArgumentException("新 Batch 不应预先包含 ID");
    if (!batch.attempts().isEmpty()) {
      throw new IllegalArgumentException("创建 Batch 时不能同时持久化 Attempt");
    }
    OfflineBatchExecutionPO po = toPO(batch);
    if (!dao.insert(po) || po.getId() == null) {
      throw new IllegalStateException("创建离线同步 BatchExecution 失败");
    }
    return new BatchExecution(
        po.getId(),
        batch.taskId(),
        batch.batchKey(),
        batch.trigger(),
        batch.batchScope(),
        batch.snapshot(),
        batch.status(),
        List.of());
  }

  @Override
  public boolean update(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    if (batch.id() == null) throw new IllegalArgumentException("更新 Batch 必须包含 ID");
    return dao.updateById(toPO(batch));
  }

  private BatchExecution toDomain(OfflineBatchExecutionPO po) {
    if (po == null) return null;
    BatchScope scope = readScope(po.getBatchScopeType(), po.getBatchScopeValue());
    String storedFingerprint = requireText(po.getBatchScopeFingerprint(), "batchScopeFingerprint 不能为空");
    if (!scope.fingerprint().equals(storedFingerprint)) {
      throw new IllegalStateException("BatchScope fingerprint 与持久化内容不一致");
    }

    List<OfflineJobExecution> legacyAttempts = executionRepository.findByBatchId(po.getId());
    String logicalJobSpec = trim(po.getLogicalJobSpecJson());
    if (logicalJobSpec == null) {
      logicalJobSpec = legacyAttempts.stream()
          .filter(attempt -> attempt.getAttemptNo() != null && attempt.getAttemptNo() == 1)
          .map(OfflineJobExecution::getSubmittedConfig)
          .filter(StringUtils::hasText)
          .findFirst()
          .map(String::trim)
          .orElse(null);
    }
    if (logicalJobSpec == null) {
      throw new IllegalStateException("Batch 缺少冻结 logicalJobSpec：" + po.getId());
    }

    RetryPolicySnapshot retryPolicy = new RetryPolicySnapshot(
        positive(po.getRetryMaxAttempts(), "retryMaxAttempts"),
        nonNegative(po.getRetryBackoffSeconds(), "retryBackoffSeconds"));
    ExecutionSnapshot snapshot = new ExecutionSnapshot(
        requireText(po.getDefinitionSnapshotJson(), "definitionSnapshot 不能为空"),
        positive(po.getDefinitionRevision(), "definitionRevision"),
        retryPolicy,
        requireText(po.getConfigDigest(), "configDigest 不能为空"),
        logicalJobSpec);
    List<ExecutionAttempt> attempts = legacyAttempts.stream()
        .map(LegacyOfflineExecutionCompatibilityMapper::toAttempt)
        .toList();
    return new BatchExecution(
        positive(po.getId(), "BatchExecutionId"),
        positive(po.getJobDefinitionId(), "TaskId"),
        new BatchKey(requireText(po.getBatchKey(), "BatchKey 不能为空")),
        enumValue(BatchTrigger.class, po.getTriggerType(), "triggerType"),
        scope,
        snapshot,
        enumValue(BatchStatus.class, po.getStatus(), "status"),
        attempts);
  }

  private OfflineBatchExecutionPO toPO(BatchExecution batch) {
    OfflineBatchExecutionPO po = new OfflineBatchExecutionPO();
    po.setId(batch.id());
    po.setJobDefinitionId(batch.taskId());
    po.setBatchKey(batch.batchKey().value());
    po.setTriggerType(batch.trigger().name());
    po.setBatchScopeType(scopeType(batch.batchScope()));
    po.setBatchScopeValue(batch.batchScope().canonicalValue());
    po.setBatchScopeFingerprint(batch.batchScope().fingerprint());
    po.setDefinitionSnapshotJson(batch.snapshot().definitionSnapshot());
    po.setDefinitionRevision(batch.snapshot().definitionRevision());
    po.setRetryMaxAttempts(batch.snapshot().retryPolicy().maxAttempts());
    po.setRetryBackoffSeconds(batch.snapshot().retryPolicy().backoffSeconds());
    po.setConfigDigest(batch.snapshot().configDigest());
    po.setLogicalJobSpecJson(batch.snapshot().logicalJobSpec());
    po.setStatus(batch.status().name());
    LocalDateTime now = LocalDateTime.now();
    if (batch.id() == null) po.setCreateTime(now);
    po.setUpdateTime(now);
    return po;
  }

  private String scopeType(BatchScope scope) {
    if (scope instanceof BatchScope.FullSelection) return "FULL_SELECTION";
    if (scope instanceof BatchScope.DataWindow) return "DATA_WINDOW";
    if (scope instanceof BatchScope.PartitionScope) return "PARTITION_SCOPE";
    if (scope instanceof BatchScope.CursorRange) return "CURSOR_RANGE";
    throw new IllegalArgumentException("不支持的 BatchScope：" + scope.getClass().getName());
  }

  private BatchScope readScope(String type, String value) {
    String normalizedType = requireText(type, "batchScopeType 不能为空").toUpperCase(Locale.ROOT);
    String canonical = requireText(value, "batchScopeValue 不能为空");
    return switch (normalizedType) {
      case "FULL_SELECTION" -> {
        if (!"FULL_SELECTION".equals(canonical)) {
          throw new IllegalStateException("FullSelection 持久化内容不正确");
        }
        yield BatchScope.fullSelection();
      }
      case "DATA_WINDOW" -> {
        String[] parts = canonical.split("\\|", -1);
        if (parts.length != 3 || !"DATA_WINDOW".equals(parts[0])) {
          throw new IllegalStateException("DataWindow 持久化内容不正确");
        }
        yield BatchScope.dataWindow(
            LocalDateTime.parse(parts[1]), LocalDateTime.parse(parts[2]));
      }
      case "PARTITION_SCOPE" -> {
        String prefix = "PARTITIONS|";
        if (!canonical.startsWith(prefix) || canonical.length() == prefix.length()) {
          throw new IllegalStateException("PartitionScope 持久化内容不正确");
        }
        yield BatchScope.partitions(
            java.util.Arrays.stream(canonical.substring(prefix.length()).split(",", -1))
                .map(this::decode)
                .toList());
      }
      case "CURSOR_RANGE" -> {
        String[] parts = canonical.split("\\|", -1);
        if (parts.length != 4 || !"CURSOR_RANGE".equals(parts[0])) {
          throw new IllegalStateException("CursorRange 持久化内容不正确");
        }
        yield BatchScope.cursorRange(decode(parts[1]), decode(parts[2]), decode(parts[3]));
      }
      default -> throw new IllegalStateException("未知 BatchScope 类型：" + normalizedType);
    };
  }

  private String decode(String value) {
    try {
      return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("BatchScope 持久化编码不正确", exception);
    }
  }

  private <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
    try {
      return Enum.valueOf(type, requireText(value, field + " 不能为空").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(field + " 持久化值不合法：" + value, exception);
    }
  }

  private String trim(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private String requireText(String value, String message) {
    String normalized = trim(value);
    if (normalized == null) throw new IllegalStateException(message);
    return normalized;
  }

  private long positive(Long value, String field) {
    if (value == null || value <= 0L) throw new IllegalStateException(field + " 必须大于 0");
    return value;
  }

  private int positive(Integer value, String field) {
    if (value == null || value <= 0) throw new IllegalStateException(field + " 必须大于 0");
    return value;
  }

  private int nonNegative(Integer value, String field) {
    if (value == null || value < 0) throw new IllegalStateException(field + " 不能小于 0");
    return value;
  }
}
