package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineBatchExecutionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步业务批次数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineBatchExecutionDaoImpl implements OfflineBatchExecutionDao {

  private final OfflineBatchExecutionMapper mapper;

  @Override
  public OfflineBatchExecutionPO selectById(Long id) {
    return mapper.selectById(id);
  }

  @Override
  public OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey) {
    if (taskId == null || taskId <= 0L || !StringUtils.hasText(batchKey)) return null;
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .eq(OfflineBatchExecutionPO::getBatchKey, batchKey.trim())
            .last("LIMIT 1"));
  }

  @Override
  public boolean existsByTaskIdAndStatuses(Long taskId, List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return false;
    return mapper.selectCount(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)) > 0L;
  }

  @Override
  public OfflineBatchExecutionPO selectLatestByTaskIdAndStatuses(
      Long taskId,
      List<String> statuses) {
    if (taskId == null || taskId <= 0L || statuses == null || statuses.isEmpty()) return null;
    return mapper.selectOne(
        Wrappers.<OfflineBatchExecutionPO>lambdaQuery()
            .eq(OfflineBatchExecutionPO::getJobDefinitionId, taskId)
            .in(OfflineBatchExecutionPO::getStatus, statuses)
            .orderByDesc(OfflineBatchExecutionPO::getId)
            .last("LIMIT 1"));
  }

  @Override
  public boolean insert(OfflineBatchExecutionPO batchPO) {
    return mapper.insert(batchPO) > 0;
  }

  @Override
  public boolean updateById(OfflineBatchExecutionPO batchPO) {
    return mapper.updateById(batchPO) > 0;
  }
}
