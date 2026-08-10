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
  boolean insert(OfflineJobExecution execution);
  boolean update(OfflineJobExecution execution);
  boolean hasActiveExecution(Long definitionId);
  List<OfflineJobExecution> findActiveExecutions(int limit);
  List<OfflineJobExecution> findRetryCandidates(LocalDateTime now, int limit);
  void markRetryCreated(Long executionId);
  PageData<OfflineJobExecution> page(OfflineExecutionQuery query);
}
