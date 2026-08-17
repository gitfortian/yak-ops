package io.yak.ops.business.quality.schedule;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 应用启动后以质量监控业务表为事实来源恢复 Yak Schedule 计划。 */
@ConditionalOnQualityEnabled
@Component
public class QualityScheduleReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(QualityScheduleReconciler.class);
  private static final int PAGE_SIZE = 100;

  private final QualityRepository repository;
  private final QualityScheduleEngineBridge engine;
  private final QualityScheduleLifecycle lifecycle;

  public QualityScheduleReconciler(
      QualityRepository repository,
      QualityScheduleEngineBridge engine,
      QualityScheduleLifecycle lifecycle) {
    this.repository = repository;
    this.engine = engine;
    this.lifecycle = lifecycle;
  }

  @Order(30)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      LOGGER.warn("[quality-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    Map<Long, Monitor> monitors = loadMonitors();
    Map<Long, MonitorSettings> scheduled = new LinkedHashMap<>();
    for (Monitor monitor : monitors.values()) {
      MonitorSettings settings = repository.findMonitorSettings(monitor.id());
      if (monitor.enabled() && settings.runMode() == RunMode.SCHEDULE) {
        scheduled.put(monitor.id(), settings);
      }
    }

    engine.list().forEach(snapshot -> {
      long monitorId;
      try {
        monitorId = Long.parseLong(snapshot.definition().key().name());
      } catch (NumberFormatException exception) {
        return;
      }
      if (!scheduled.containsKey(monitorId)) {
        try {
          engine.deleteIfPresent(monitorId);
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "[quality-schedule] stale schedule cleanup failed monitor={}, message={}",
              monitorId,
              exception.getMessage());
        }
      }
    });

    LocalDateTime now = LocalDateTime.now();
    scheduled.forEach((monitorId, settings) -> {
      LocalDateTime persistedNextRunTime = settings.nextRunTime();
      try {
        lifecycle.sync(monitorId);
        LOGGER.info("[quality-schedule] reconciled monitor={}", monitorId);
      } catch (RuntimeException exception) {
        LOGGER.error(
            "[quality-schedule] reconcile failed monitor={}, message={}",
            monitorId,
            exception.getMessage(),
            exception);
        return;
      }

      if (persistedNextRunTime != null && !persistedNextRunTime.isAfter(now)) {
        try {
          engine.runNowIfPresent(monitorId);
          LOGGER.info(
              "[quality-schedule] recovered missed trigger monitor={}, planned={}",
              monitorId,
              persistedNextRunTime);
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "[quality-schedule] missed trigger recovery skipped monitor={}, planned={}, message={}",
              monitorId,
              persistedNextRunTime,
              exception.getMessage());
        }
      }
    });
  }

  private Map<Long, Monitor> loadMonitors() {
    Map<Long, Monitor> result = new LinkedHashMap<>();
    int current = 1;
    while (true) {
      var page = repository.pageMonitors(new QualityQuery.Monitor(
          current, PAGE_SIZE, null, null, null, false, null, false, null, null, null));
      page.records().forEach(monitor -> result.put(monitor.id(), monitor));
      if (page.records().size() < PAGE_SIZE) break;
      current++;
    }
    return result;
  }
}
