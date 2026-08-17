package io.yak.ops.business.quality.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Yak Ops 数据质量调度到 Yak Framework ScheduleManager 的适配层。 */
@Component
public class QualityScheduleEngineBridge {
  static final String NAMESPACE = "yak-ops-quality";
  static final String HANDLER = "qualityScheduleHandler";

  private final ObjectProvider<ScheduleManager> scheduleManagers;
  private final QualityScheduleCalculator calculator;

  public QualityScheduleEngineBridge(
      ObjectProvider<ScheduleManager> scheduleManagers,
      QualityScheduleCalculator calculator) {
    this.scheduleManagers = scheduleManagers;
    this.calculator = calculator;
  }

  public boolean available() {
    return scheduleManagers.getIfAvailable() != null;
  }

  public ScheduleSnapshot save(Monitor monitor, MonitorSettings settings) {
    return manager().save(toDefinition(monitor, settings));
  }

  public Optional<ScheduleSnapshot> snapshot(long monitorId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? Optional.empty() : manager.get(key(monitorId));
  }

  public List<ScheduleSnapshot> list() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? List.of() : manager.list(NAMESPACE);
  }

  public void pauseIfPresent(long monitorId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(monitorId);
    if (manager.get(key).isPresent()) manager.pause(key);
  }

  public void deleteIfPresent(long monitorId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(monitorId);
    if (manager.get(key).isPresent()) manager.delete(key);
  }

  ScheduleDefinition toDefinition(Monitor monitor, MonitorSettings settings) {
    if (monitor == null || monitor.id() == null) {
      throw new IllegalArgumentException("质量监控不能为空");
    }
    if (settings == null || settings.runMode() != RunMode.SCHEDULE) {
      throw new IllegalArgumentException("质量监控未配置调度触发");
    }

    String cron = calculator.cronExpression(
        settings.scheduleFrequency(),
        settings.scheduleTime(),
        settings.scheduleWeekday(),
        settings.cronExpression());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("monitorId", monitor.id());

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("monitorId", String.valueOf(monitor.id()));
    metadata.put("dataSourceId", String.valueOf(monitor.dataSourceId()));
    metadata.put("tableName", monitor.tableName());

    return new ScheduleDefinition(
        key(monitor.id()),
        monitor.name(),
        ScheduleTrigger.cron(cron, ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        monitor.enabled(),
        metadata);
  }

  private ScheduleKey key(long monitorId) {
    if (monitorId <= 0L) throw new IllegalArgumentException("质量监控 ID 不合法");
    return new ScheduleKey(NAMESPACE, String.valueOf(monitorId));
  }

  private ScheduleManager manager() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) {
      throw new IllegalStateException(
          "Yak ScheduleManager 不可用，请确认 yak-schedule-core、调度引擎插件与 yak.schedule.enabled 配置");
    }
    return manager;
  }
}
