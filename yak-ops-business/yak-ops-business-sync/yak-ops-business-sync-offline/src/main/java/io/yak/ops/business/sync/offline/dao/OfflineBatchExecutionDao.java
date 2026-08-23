package io.yak.ops.business.sync.offline.dao;

import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.util.List;

/** 离线同步业务批次数据访问接口。 */
public interface OfflineBatchExecutionDao {

  OfflineBatchExecutionPO selectById(Long id);

  OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey);

  boolean existsByTaskIdAndStatuses(Long taskId, List<String> statuses);

  OfflineBatchExecutionPO selectLatestByTaskIdAndStatuses(Long taskId, List<String> statuses);

  boolean insert(OfflineBatchExecutionPO batchPO);

  boolean updateById(OfflineBatchExecutionPO batchPO);
}
