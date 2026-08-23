package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import java.util.Optional;

/** 离线同步业务批次领域仓储。 */
public interface OfflineBatchExecutionRepository {

  Optional<BatchExecution> findById(Long id);

  Optional<BatchExecution> findByTaskIdAndBatchKey(long taskId, BatchKey batchKey);

  /** V1 Task runtime truth：RUNNING / WAITING_RETRY / UNKNOWN Batch 会占用任务执行槽位。 */
  boolean hasOccupyingBatch(long taskId);

  /** 返回任务最近一个占用执行槽位的 Batch；命令判断不得回退到 Task last-*。 */
  Optional<BatchExecution> findLatestOccupyingByTaskId(long taskId);

  BatchExecution insert(BatchExecution batch);

  boolean update(BatchExecution batch);
}
