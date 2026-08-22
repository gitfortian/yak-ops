package io.yak.ops.business.sync.offline.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineExecutionEventMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineWriteMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 基于 MyBatis-Plus 的离线同步任务定义数据访问实现。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineJobDefinitionDaoImpl implements OfflineJobDefinitionDao {

  private static final List<String> ACTIVE_STATUSES =
      List.of("CREATED", "SUBMITTED", "QUEUED", "RUNNING");

  private final OfflineJobDefinitionMapper mapper;
  private final OfflineJobExecutionMapper executionMapper;
  private final OfflineExecutionEventMapper eventMapper;
  private final OfflineWriteMapper writeMapper;

  @Override
  public OfflineJobDefinitionPO selectById(Long id) {
    return mapper.selectById(id);
  }

  @Override
  public boolean insert(OfflineJobDefinitionPO definitionPO) {
    return mapper.insert(definitionPO) > 0;
  }

  @Override
  public boolean updateById(OfflineJobDefinitionPO definitionPO) {
    return mapper.updateById(definitionPO) > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    List<Long> executionIds = executionMapper.selectObjs(
            Wrappers.<OfflineJobExecutionPO>query()
                .select("id")
                .eq("job_definition_id", id))
        .stream()
        .filter(Number.class::isInstance)
        .map(Number.class::cast)
        .map(Number::longValue)
        .toList();

    boolean deleted = mapper.deleteById(id) > 0;
    if (!deleted) return false;

    if (!executionIds.isEmpty()) {
      eventMapper.delete(
          Wrappers.<OfflineExecutionEventPO>lambdaQuery()
              .in(OfflineExecutionEventPO::getExecutionId, executionIds));
    }
    executionMapper.delete(
        Wrappers.<OfflineJobExecutionPO>lambdaQuery()
            .eq(OfflineJobExecutionPO::getJobDefinitionId, id));
    return true;
  }

  @Override
  public boolean existsByName(String jobName, Long excludeId) {
    LambdaQueryWrapper<OfflineJobDefinitionPO> query =
        new LambdaQueryWrapper<OfflineJobDefinitionPO>()
            .eq(OfflineJobDefinitionPO::getJobName, jobName);
    if (excludeId != null) query.ne(OfflineJobDefinitionPO::getId, excludeId);
    return mapper.selectCount(query) > 0L;
  }

  @Override
  public IPage<OfflineJobDefinitionPO> selectPage(PageQuery query) {
    PageQuery condition = query == null
        ? new PageQuery(1, 10, null, null, null, null, null, null, null, null, null)
        : query;
    LambdaQueryWrapper<OfflineJobDefinitionPO> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(condition.jobName())) {
      wrapper.like(OfflineJobDefinitionPO::getJobName, condition.jobName().trim());
    }
    if (condition.id() != null && condition.id() > 0L) {
      wrapper.eq(OfflineJobDefinitionPO::getId, condition.id());
    }
    if (StringUtils.hasText(condition.status())) {
      String status = normalizeStatus(condition.status());
      if ("RUNNING".equals(status)) {
        wrapper.in(OfflineJobDefinitionPO::getLastJobStatus, ACTIVE_STATUSES);
      } else {
        wrapper.eq(OfflineJobDefinitionPO::getLastJobStatus, status);
      }
    }
    addLike(wrapper, OfflineJobDefinitionPO::getSourceType, condition.sourceType());
    addLike(wrapper, OfflineJobDefinitionPO::getSinkType, condition.sinkType());
    addLike(wrapper, OfflineJobDefinitionPO::getSourceTable, condition.sourceTable());
    addLike(wrapper, OfflineJobDefinitionPO::getSinkTable, condition.sinkTable());
    if (condition.createTimeStart() != null) {
      wrapper.ge(OfflineJobDefinitionPO::getCreateTime, condition.createTimeStart());
    }
    if (condition.createTimeEnd() != null) {
      wrapper.le(OfflineJobDefinitionPO::getCreateTime, condition.createTimeEnd());
    }
    wrapper.orderByDesc(OfflineJobDefinitionPO::getUpdateTime)
        .orderByDesc(OfflineJobDefinitionPO::getId);
    return mapper.selectPage(
        new Page<>(Math.max(1, condition.current()), Math.max(1, condition.pageSize())),
        wrapper);
  }

  @Override
  public List<OfflineJobDefinitionPO> selectWithCron() {
    return mapper.selectList(
        new LambdaQueryWrapper<OfflineJobDefinitionPO>()
            .isNotNull(OfflineJobDefinitionPO::getCronExpression)
            .orderByAsc(OfflineJobDefinitionPO::getId));
  }

  @Override
  public Long lockById(Long id) {
    return writeMapper.lockDefinition(id);
  }

  @Override
  public boolean updateSchedule(
      Long id,
      String scheduleJson,
      boolean enabled,
      String cronExpression,
      int retryMaxAttempts,
      int retryBackoffSeconds,
      LocalDateTime nextFireTime,
      LocalDateTime updateTime) {
    return mapper.update(
        null,
        Wrappers.<OfflineJobDefinitionPO>lambdaUpdate()
            .eq(OfflineJobDefinitionPO::getId, id)
            .set(OfflineJobDefinitionPO::getScheduleJson, scheduleJson)
            .set(OfflineJobDefinitionPO::getScheduleEnabled, enabled)
            .set(OfflineJobDefinitionPO::getCronExpression, cronExpression)
            .set(OfflineJobDefinitionPO::getRetryMaxAttempts, retryMaxAttempts)
            .set(OfflineJobDefinitionPO::getRetryBackoffSeconds, retryBackoffSeconds)
            .set(OfflineJobDefinitionPO::getScheduleNextFireTime, nextFireTime)
            .set(OfflineJobDefinitionPO::getUpdateTime, updateTime)) > 0;
  }

  @Override
  public void updateScheduleRuntime(
      Long id,
      LocalDateTime lastFireTime,
      LocalDateTime nextFireTime,
      LocalDateTime updateTime) {
    mapper.update(
        null,
        Wrappers.<OfflineJobDefinitionPO>lambdaUpdate()
            .eq(OfflineJobDefinitionPO::getId, id)
            .set(OfflineJobDefinitionPO::getScheduleLastFireTime, lastFireTime)
            .set(OfflineJobDefinitionPO::getScheduleNextFireTime, nextFireTime)
            .set(OfflineJobDefinitionPO::getUpdateTime, updateTime));
  }

  @Override
  public void clearSchedule(Long id, LocalDateTime updateTime) {
    mapper.update(
        null,
        Wrappers.<OfflineJobDefinitionPO>lambdaUpdate()
            .eq(OfflineJobDefinitionPO::getId, id)
            .set(OfflineJobDefinitionPO::getScheduleJson, null)
            .set(OfflineJobDefinitionPO::getScheduleEnabled, false)
            .set(OfflineJobDefinitionPO::getCronExpression, null)
            .set(OfflineJobDefinitionPO::getRetryMaxAttempts, 1)
            .set(OfflineJobDefinitionPO::getRetryBackoffSeconds, 60)
            .set(OfflineJobDefinitionPO::getScheduleLastFireTime, null)
            .set(OfflineJobDefinitionPO::getScheduleNextFireTime, null)
            .set(OfflineJobDefinitionPO::getUpdateTime, updateTime));
  }

  private <T> void addLike(
      LambdaQueryWrapper<OfflineJobDefinitionPO> query,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<OfflineJobDefinitionPO, T> column,
      String value) {
    if (StringUtils.hasText(value)) query.like(column, value.trim());
  }

  private String normalizeStatus(String status) {
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    return "COMPLETED".equals(normalized) || "FINISHED".equals(normalized)
        ? "SUCCEEDED"
        : normalized;
  }
}
