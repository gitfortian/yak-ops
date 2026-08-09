package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 将任务级调度投影持久化到任务定义表。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineScheduleRepositoryAdapter implements OfflineScheduleRepository {
  private final OfflineJobDefinitionDao dao;

  @Override
  public OfflineSchedule saveSchedule(OfflineSchedule schedule) {
    if (schedule == null || schedule.jobDefinitionId() == null) {
      throw new IllegalArgumentException("调度配置缺少任务定义 ID");
    }
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    po.setId(schedule.jobDefinitionId());
    po.setScheduleJson(schedule.scheduleJson());
    po.setScheduleEnabled(schedule.enabled());
    po.setCronExpression(schedule.cronExpression());
    po.setRetryMaxAttempts(Math.max(1, schedule.retryMaxAttempts()));
    po.setRetryBackoffSeconds(Math.max(1, schedule.retryBackoffSeconds()));
    po.setScheduleLastFireTime(schedule.lastFireTime());
    po.setScheduleNextFireTime(schedule.nextFireTime());
    po.setUpdateTime(LocalDateTime.now());
    if (!dao.updateById(po)) throw new IllegalArgumentException("离线同步任务不存在：" + schedule.jobDefinitionId());
    return findSchedule(schedule.jobDefinitionId());
  }

  @Override
  public OfflineSchedule findSchedule(Long definitionId) {
    return from(dao.selectById(definitionId));
  }

  @Override
  public List<OfflineSchedule> findAllSchedules() {
    return dao.selectWithCron().stream().map(this::from).toList();
  }

  @Override
  public void updateRuntimeState(Long definitionId, LocalDateTime last, LocalDateTime next) {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    po.setId(definitionId);
    po.setScheduleLastFireTime(last);
    po.setScheduleNextFireTime(next);
    po.setUpdateTime(LocalDateTime.now());
    dao.updateById(po);
  }

  @Override
  public void deleteSchedule(Long definitionId) {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    po.setId(definitionId);
    po.setScheduleJson(null);
    po.setScheduleEnabled(false);
    po.setCronExpression(null);
    po.setRetryMaxAttempts(1);
    po.setRetryBackoffSeconds(60);
    po.setScheduleLastFireTime(null);
    po.setScheduleNextFireTime(null);
    po.setUpdateTime(LocalDateTime.now());
    dao.updateById(po);
  }

  private OfflineSchedule from(OfflineJobDefinitionPO po) {
    if (po == null) return null;
    return new OfflineSchedule(
        po.getId(),
        po.getCronExpression(),
        Boolean.TRUE.equals(po.getScheduleEnabled()),
        po.getRetryMaxAttempts() == null ? 1 : po.getRetryMaxAttempts(),
        po.getRetryBackoffSeconds() == null ? 60 : po.getRetryBackoffSeconds(),
        po.getScheduleNextFireTime(),
        po.getScheduleLastFireTime(),
        po.getScheduleJson());
  }
}
