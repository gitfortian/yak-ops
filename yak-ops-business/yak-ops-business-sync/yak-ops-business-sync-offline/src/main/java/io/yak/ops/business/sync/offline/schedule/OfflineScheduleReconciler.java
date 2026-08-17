package io.yak.ops.business.sync.offline.schedule;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 应用启动后以离线同步业务表为事实来源恢复 Yak Schedule 计划。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineScheduleReconciler.class);

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineScheduleEngineBridge engine;
  private final OfflineScheduleLifecycle lifecycle;

  public OfflineScheduleReconciler(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineScheduleEngineBridge engine,
      OfflineScheduleLifecycle lifecycle) {
    this.definitionRepository = definitionRepository;
    this.scheduleRepository = scheduleRepository;
    this.engine = engine;
    this.lifecycle = lifecycle;
  }

  @Order(40)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      LOGGER.warn("[offline-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    Map<Long, OfflineSchedule> active = activeSchedules();
    engine.list().forEach(snapshot -> {
      long definitionId;
      try {
        definitionId = Long.parseLong(snapshot.definition().key().name());
      } catch (NumberFormatException exception) {
        return;
      }
      if (!active.containsKey(definitionId)) {
        try {
          engine.deleteIfPresent(definitionId);
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "[offline-schedule] stale schedule cleanup failed definition={}, message={}",
              definitionId,
              exception.getMessage());
        }
      }
    });

    LocalDateTime now = LocalDateTime.now();
    active.forEach((definitionId, schedule) -> {
      LocalDateTime persistedNextFireTime = schedule.nextFireTime();
      try {
        lifecycle.sync(definitionId);
        LOGGER.info("[offline-schedule] reconciled definition={}", definitionId);
      } catch (RuntimeException exception) {
        LOGGER.error(
            "[offline-schedule] reconcile failed definition={}, message={}",
            definitionId,
            exception.getMessage(),
            exception);
        return;
      }

      if (persistedNextFireTime != null && !persistedNextFireTime.isAfter(now)) {
        try {
          engine.runNowIfPresent(definitionId);
          LOGGER.info(
              "[offline-schedule] recovered missed trigger definition={}, planned={}",
              definitionId,
              persistedNextFireTime);
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "[offline-schedule] missed trigger recovery skipped definition={}, planned={}, message={}",
              definitionId,
              persistedNextFireTime,
              exception.getMessage());
        }
      }
    });
  }

  private Map<Long, OfflineSchedule> activeSchedules() {
    Map<Long, OfflineSchedule> result = new LinkedHashMap<>();
    for (OfflineSchedule schedule : scheduleRepository.findAllSchedules()) {
      if (schedule == null
          || schedule.jobDefinitionId() == null
          || !schedule.enabled()
          || !StringUtils.hasText(schedule.cronExpression())) {
        continue;
      }
      OfflineJobDefinition definition = definitionRepository.findById(schedule.jobDefinitionId()).orElse(null);
      if (definition != null && "ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
        result.put(schedule.jobDefinitionId(), schedule);
      }
    }
    return result;
  }
}
