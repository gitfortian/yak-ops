package io.yak.ops.business.sync.offline.service;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch runtime truth boundary.
 *
 * <p>BatchExecution 是业务运行真相，状态只由同 Batch 的 latest Attempt 推导。Task last-* 只允许作为查询投影，
 * 不得进入这里的命令判断。
 */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineBatchRuntimeService {

  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineCursorService cursorService;

  public boolean hasOccupyingBatch(Long taskId) {
    return batchRepository.hasOccupyingBatch(positive(taskId, "TaskId"));
  }

  public Optional<BatchExecution> findLatestOccupyingBatch(Long taskId) {
    return batchRepository.findLatestOccupyingByTaskId(positive(taskId, "TaskId"));
  }

  public BatchExecution requireLatestOccupyingBatch(Long taskId) {
    return findLatestOccupyingBatch(taskId)
        .orElseThrow(() -> new IllegalStateException("任务没有运行中的 BatchExecution"));
  }

  /** 返回 Batch latest Attempt 的 legacy persistence view；仅作为过渡期 Attempt 存储模型使用。 */
  public OfflineJobExecution requireLatestAttempt(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    ExecutionAttempt latest = batch.latestAttempt()
        .orElseThrow(() -> new IllegalStateException("BatchExecution 缺少 Attempt"));
    if (latest.id() == null || latest.id() <= 0L) {
      throw new IllegalStateException("latest Attempt 缺少持久化 ID");
    }
    OfflineJobExecution execution = executionRepository.findById(latest.id())
        .orElseThrow(() -> new IllegalStateException("latest Attempt persistence 不存在：" + latest.id()));
    if (!Objects.equals(execution.getBatchId(), batch.id())) {
      throw new IllegalStateException("latest Attempt 与 BatchExecution 绑定不一致");
    }
    return execution;
  }

  /** Attempt 与 Batch status 在同一事务内 dual-write。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void persistAttempt(OfflineJobExecution execution) {
    Objects.requireNonNull(execution, "Attempt persistence 不能为空");
    if (execution.getId() == null || execution.getId() <= 0L) {
      throw new IllegalArgumentException("AttemptId 必须大于 0");
    }
    if (!executionRepository.update(execution)) {
      throw new IllegalStateException("更新离线同步 Attempt 失败：" + execution.getId());
    }
    refreshBatch(execution.getBatchId());
  }

  /**
   * 从持久化的 latest Attempt 重新计算 BatchStatus。
   *
   * <p>不得直接使用调用方传入 Attempt 的状态，否则旧 Attempt 的晚到 reconcile 可能回退 Batch 真相。Wave 5
   * 同时把 Cursor advancement 放在同一事务中，保证只有 SUCCEEDED Batch 才能推进 Cursor。
   */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void refreshBatch(Long batchId) {
    if (batchId == null || batchId <= 0L) return;
    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("Attempt 绑定的 BatchExecution 不存在：" + batchId));
    List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batchId);
    if (attempts.isEmpty()) return;

    OfflineJobExecution latest = attempts.stream()
        .max(
            Comparator.comparingInt((OfflineJobExecution value) -> number(value.getAttemptNo(), 1))
                .thenComparingLong(value -> number(value.getId(), 0L)))
        .orElseThrow();
    BatchStatus target = deriveStatus(latest);
    if (batch.status() == target) {
      if (target == BatchStatus.SUCCEEDED) {
        cursorService.advanceAfterSucceededBatch(batch);
      }
      return;
    }

    BatchExecution updated = new BatchExecution(
        batch.id(),
        batch.taskId(),
        batch.batchKey(),
        batch.trigger(),
        batch.batchScope(),
        batch.snapshot(),
        target,
        batch.attempts());
    if (!batchRepository.update(updated)) {
      throw new IllegalStateException("更新 BatchExecution runtime status 失败：" + batch.id());
    }
    if (target == BatchStatus.SUCCEEDED) {
      cursorService.advanceAfterSucceededBatch(updated);
    }
  }

  /** WAITING_RETRY 没有活动引擎 Attempt，停止语义是原子占用 Retry reservation 后取消 Batch。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineJobExecution cancelWaitingRetry(BatchExecution batch) {
    Objects.requireNonNull(batch, "BatchExecution 不能为空");
    if (batch.status() != BatchStatus.WAITING_RETRY) {
      throw new IllegalStateException("只有 WAITING_RETRY Batch 可以直接取消 Retry 等待");
    }
    OfflineJobExecution latest = requireLatestAttempt(batch);
    if (OfflineExecutionStatus.parse(latest.getStatus()) != OfflineExecutionStatus.FAILED) {
      throw new IllegalStateException("WAITING_RETRY Batch 的 latest Attempt 必须是 FAILED");
    }

    // 与自动 Retry 使用同一个 CAS reservation，防止“取消等待”和“创建下一 Attempt”并发穿透。
    if (!executionRepository.reserveRetry(latest.getId())) {
      throw new IllegalStateException("Retry 已被其他请求保留，无法安全取消等待");
    }
    latest.setNextRetryTime(null);
    latest.setRetryCreated(true);
    latest.setUpdateTime(LocalDateTime.now());

    BatchExecution canceled = new BatchExecution(
        batch.id(),
        batch.taskId(),
        batch.batchKey(),
        batch.trigger(),
        batch.batchScope(),
        batch.snapshot(),
        BatchStatus.CANCELED,
        batch.attempts());
    if (!batchRepository.update(canceled)) {
      throw new IllegalStateException("取消 WAITING_RETRY Batch 失败：" + batch.id());
    }
    return latest;
  }

  BatchStatus deriveStatus(OfflineJobExecution latest) {
    OfflineExecutionStatus status = OfflineExecutionStatus.parse(latest.getStatus());
    return switch (status) {
      case CREATED, SUBMITTED, QUEUED, RUNNING -> BatchStatus.RUNNING;
      case UNKNOWN, LOST -> BatchStatus.UNKNOWN;
      case SUCCEEDED -> BatchStatus.SUCCEEDED;
      case CANCELED -> BatchStatus.CANCELED;
      case FAILED -> latest.getNextRetryTime() == null
          ? BatchStatus.FAILED
          : BatchStatus.WAITING_RETRY;
    };
  }

  private long positive(Long value, String field) {
    if (value == null || value <= 0L) throw new IllegalArgumentException(field + " 必须大于 0");
    return value;
  }

  private int number(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private long number(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
