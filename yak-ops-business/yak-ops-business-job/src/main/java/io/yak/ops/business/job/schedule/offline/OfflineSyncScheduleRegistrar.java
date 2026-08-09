package io.yak.ops.business.job.schedule.offline;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.job.schedule.JobScheduleProperties;
import io.yak.ops.business.job.schedule.JobScheduleRegistrar;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将离线同步调度配置注册到 Yak Schedule。 */
@Component
@ConditionalOnOfflineSyncEnabled
@ConditionalOnProperty(
    prefix = "yak.job.schedule",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OfflineSyncScheduleRegistrar implements JobScheduleRegistrar {

  private final ScheduleManager scheduleManager;
  private final OfflineScheduleRepository scheduleRepository;
  private final JobScheduleProperties properties;

  public OfflineSyncScheduleRegistrar(
      ScheduleManager scheduleManager,
      OfflineScheduleRepository scheduleRepository,
      JobScheduleProperties properties) {
    this.scheduleManager = scheduleManager;
    this.scheduleRepository = scheduleRepository;
    this.properties = properties;
  }

  @Override
  public String registrationType() {
    return OfflineSyncScheduleConstants.NAMESPACE;
  }

  @Override
  public void synchronize() {
    List<OfflineSchedule> records = scheduleRepository.findAllSchedules();
    Set<ScheduleKey> desiredKeys = new LinkedHashSet<>();

    for (OfflineSchedule record : records) {
      if (!StringUtils.hasText(record.cronExpression())) continue;
      ScheduleDefinition desired = definition(record);
      desiredKeys.add(desired.key());

      Optional<ScheduleSnapshot> current = scheduleManager.get(desired.key());
      ScheduleSnapshot snapshot =
          current.isPresent() && desired.equals(current.get().definition())
              ? current.get()
              : scheduleManager.save(desired);

      scheduleRepository.updateRuntimeState(
          record.jobDefinitionId(),
          localDateTime(snapshot.lastFireTime()),
          localDateTime(snapshot.nextFireTime()));
    }

    for (ScheduleSnapshot snapshot : scheduleManager.list(OfflineSyncScheduleConstants.NAMESPACE)) {
      if (!desiredKeys.contains(snapshot.definition().key())) {
        scheduleManager.delete(snapshot.definition().key());
      }
    }
  }

  ScheduleDefinition definition(OfflineSchedule record) {
    Long definitionId = record.jobDefinitionId();
    return new ScheduleDefinition(
        OfflineSyncScheduleConstants.key(definitionId),
        "离线同步任务定义 " + definitionId,
        ScheduleTrigger.cron(record.cronExpression(), zoneId()),
        new ScheduleTarget(
            OfflineSyncScheduleConstants.HANDLER_NAME,
            Map.of(OfflineSyncScheduleConstants.PAYLOAD_DEFINITION_ID, definitionId.toString())),
        new SchedulePolicy(ConcurrencyPolicy.FORBID, MisfirePolicy.FIRE_ONCE_NOW, 0),
        record.enabled(),
        Map.of("businessType", "OFFLINE_SYNC", "definitionId", definitionId.toString()));
  }

  private ZoneId zoneId() {
    String configured = properties.getZoneId();
    return ZoneId.of(StringUtils.hasText(configured) ? configured.trim() : "Asia/Shanghai");
  }

  private LocalDateTime localDateTime(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, zoneId());
  }
}
