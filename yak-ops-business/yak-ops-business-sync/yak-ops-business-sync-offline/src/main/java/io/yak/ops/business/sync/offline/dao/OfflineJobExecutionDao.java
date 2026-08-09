package io.yak.ops.business.sync.offline.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.List;

/** 离线同步任务实例数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface OfflineJobExecutionDao {

  OfflineJobExecutionPO selectById(Long id);

  OfflineJobExecutionPO selectByIdempotencyKey(String idempotencyKey);

  boolean insert(OfflineJobExecutionPO executionPO);

  boolean updateById(OfflineJobExecutionPO executionPO);

  boolean hasActiveExecution(Long definitionId);

  List<OfflineJobExecutionPO> selectActiveExecutions(int limit);

  List<OfflineJobExecutionPO> selectRetryCandidates(LocalDateTime now, int limit);

  void markRetryCreated(Long executionId, LocalDateTime updateTime);

  IPage<OfflineJobExecutionPO> selectPage(PageQuery query);

  record PageQuery(
      int current,
      int pageSize,
      Long jobDefinitionId,
      String status) {}
}
