package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import java.util.List;
import java.util.Optional;

/** 离线同步业务批次领域仓储。 */
public interface OfflineBatchExecutionRepository {

  Optional<BatchExecution> findById(Long id);

  Optional<BatchExecution> findByTaskIdAndBatchKey(long taskId, BatchKey batchKey);

  /** V1 Task runtime truth：RUNNING / WAITING_RETRY / UNKNOWN Batch 会占用任务执行槽位。 */
  boolean hasOccupyingBatch(long taskId);

  /** 返回任务最近一个占用执行槽位的 Batch；命令判断不得回退到 Task last-*。 */
  Optional<BatchExecution> findLatestOccupyingByTaskId(long taskId);

  /** Wave 5 Backfill queue：只返回尚未创建 Attempt 1 的 PENDING Backfill Batch。 */
  List<BatchExecution> findPendingBackfills(int limit);

  /** PENDING -> RUNNING CAS reservation，防止多节点重复创建 Backfill Attempt 1。 */
  boolean reservePendingBackfill(long batchId);

  BatchExecution insert(BatchExecution batch);

  boolean update(BatchExecution batch);
}
