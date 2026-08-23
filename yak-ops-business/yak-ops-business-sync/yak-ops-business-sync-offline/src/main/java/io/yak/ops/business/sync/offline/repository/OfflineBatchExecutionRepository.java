package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import java.util.Optional;

/** 离线同步业务批次领域仓储。 */
public interface OfflineBatchExecutionRepository {

  Optional<BatchExecution> findById(Long id);

  Optional<BatchExecution> findByTaskIdAndBatchKey(long taskId, BatchKey batchKey);

  BatchExecution insert(BatchExecution batch);

  boolean update(BatchExecution batch);
}
