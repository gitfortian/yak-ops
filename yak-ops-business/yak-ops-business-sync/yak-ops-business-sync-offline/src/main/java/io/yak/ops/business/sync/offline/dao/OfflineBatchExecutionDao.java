package io.yak.ops.business.sync.offline.dao;

import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;

/** 离线同步业务批次数据访问接口。 */
public interface OfflineBatchExecutionDao {

  OfflineBatchExecutionPO selectById(Long id);

  OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey);

  boolean insert(OfflineBatchExecutionPO batchPO);

  boolean updateById(OfflineBatchExecutionPO batchPO);
}
