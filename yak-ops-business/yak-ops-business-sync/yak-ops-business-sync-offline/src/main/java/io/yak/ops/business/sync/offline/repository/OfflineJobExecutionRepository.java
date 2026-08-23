package io.yak.ops.business.sync.offline.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 离线同步执行实例领域仓储。 */
public interface OfflineJobExecutionRepository {
  Optional<OfflineJobExecution> findById(Long id);
  Optional<OfflineJobExecution> findByIdempotencyKey(String idempotencyKey);
  List<OfflineJobExecution> findByBatchId(Long batchId);
  boolean insert(OfflineJobExecution execution);
  boolean update(OfflineJobExecution execution);
  boolean bindBatch(Long executionId, Long batchId);
  boolean hasActiveExecution(Long definitionId);
  List<OfflineJobExecution> findActiveExecutions(int limit);
  List<OfflineJobExecution> findRetryCandidates(LocalDateTime now, int limit);
  /** 原子保留一次 FAILED Attempt 的 Retry 创建权。 */
  boolean reserveRetry(Long executionId);
  PageData<OfflineJobExecution> page(OfflineExecutionQuery query);
}
