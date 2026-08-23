package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步任务实例数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineJobExecutionDaoImpl implements OfflineJobExecutionDao {

  private static final List<String> ACTIVE_STATUSES =
      List.of("CREATED", "SUBMITTED", "QUEUED", "RUNNING", "UNKNOWN", "LOST");

  private final OfflineJobExecutionMapper mapper;

  @Override
  public OfflineJobExecutionPO selectById(Long id) {
    return mapper.selectById(id);
  }

  @Override
  public OfflineJobExecutionPO selectByIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) return null;
    return mapper.selectOne(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getIdempotencyKey, idempotencyKey.trim())
            .last("LIMIT 1"));
  }

  @Override
  public List<OfflineJobExecutionPO> selectByBatchId(Long batchId) {
    if (batchId == null || batchId <= 0L) return List.of();
    return mapper.selectList(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getBatchId, batchId)
            .orderByAsc(OfflineJobExecutionPO::getAttemptNo)
            .orderByAsc(OfflineJobExecutionPO::getId));
  }

  @Override
  public boolean insert(OfflineJobExecutionPO executionPO) {
    return mapper.insert(executionPO) > 0;
  }

  @Override
  public boolean updateById(OfflineJobExecutionPO executionPO) {
    return mapper.updateById(executionPO) > 0;
  }

  @Override
  public boolean bindBatch(Long executionId, Long batchId, LocalDateTime updateTime) {
    if (executionId == null || executionId <= 0L || batchId == null || batchId <= 0L) return false;
    return mapper.update(
        null,
        Wrappers.<OfflineJobExecutionPO>lambdaUpdate()
            .eq(OfflineJobExecutionPO::getId, executionId)
            .and(
                condition -> condition
                    .isNull(OfflineJobExecutionPO::getBatchId)
                    .or()
                    .eq(OfflineJobExecutionPO::getBatchId, batchId))
            .set(OfflineJobExecutionPO::getBatchId, batchId)
            .set(OfflineJobExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public boolean hasActiveExecution(Long definitionId) {
    return mapper.selectCount(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getJobDefinitionId, definitionId)
            .in(OfflineJobExecutionPO::getStatus, ACTIVE_STATUSES)) > 0L;
  }

  @Override
  public List<OfflineJobExecutionPO> selectActiveExecutions(int limit) {
    return mapper.selectList(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .in(OfflineJobExecutionPO::getStatus, ACTIVE_STATUSES)
            .orderByAsc(OfflineJobExecutionPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public List<OfflineJobExecutionPO> selectRetryCandidates(LocalDateTime now, int limit) {
    return mapper.selectList(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getStatus, "FAILED")
            .eq(OfflineJobExecutionPO::getRetryCreated, false)
            .isNotNull(OfflineJobExecutionPO::getBatchId)
            .isNotNull(OfflineJobExecutionPO::getNextRetryTime)
            .le(OfflineJobExecutionPO::getNextRetryTime, now)
            .orderByAsc(OfflineJobExecutionPO::getNextRetryTime)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public boolean reserveRetry(Long executionId, LocalDateTime updateTime) {
    if (executionId == null || executionId <= 0L) return false;
    return mapper.update(
        null,
        Wrappers.<OfflineJobExecutionPO>lambdaUpdate()
            .eq(OfflineJobExecutionPO::getId, executionId)
            .eq(OfflineJobExecutionPO::getStatus, "FAILED")
            .eq(OfflineJobExecutionPO::getRetryCreated, false)
            .isNotNull(OfflineJobExecutionPO::getBatchId)
            .set(OfflineJobExecutionPO::getRetryCreated, true)
            .set(OfflineJobExecutionPO::getNextRetryTime, null)
            .set(OfflineJobExecutionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public IPage<OfflineJobExecutionPO> selectPage(PageQuery query) {
    PageQuery condition = query == null ? new PageQuery(1, 10, null, null) : query;
    LambdaQueryWrapper<OfflineJobExecutionPO> wrapper = new LambdaQueryWrapper<>();
    if (condition.jobDefinitionId() != null && condition.jobDefinitionId() > 0L) {
      wrapper.eq(OfflineJobExecutionPO::getJobDefinitionId, condition.jobDefinitionId());
    }
    if (StringUtils.hasText(condition.status())) {
      wrapper.eq(
          OfflineJobExecutionPO::getStatus,
          condition.status().trim().toUpperCase(Locale.ROOT));
    }
    wrapper.orderByDesc(OfflineJobExecutionPO::getId);
    return mapper.selectPage(
        new Page<>(Math.max(1, condition.current()), Math.max(1, condition.pageSize())),
        wrapper);
  }
}
