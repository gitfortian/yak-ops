package io.yak.ops.business.sync.offline.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 离线同步业务调度到 Yak Framework ScheduleManager 的适配层。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleEngineBridge {
  static final String NAMESPACE = "yak-ops-offline-sync";
  static final String HANDLER = "offlineSyncScheduleHandler";

  private final ObjectProvider<ScheduleManager> scheduleManagers;

  public OfflineScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.scheduleManagers = scheduleManagers;
  }

  public boolean available() {
    return scheduleManagers.getIfAvailable() != null;
  }

  public ScheduleSnapshot save(OfflineJobDefinition definition, OfflineSchedule schedule) {
    return manager().save(toDefinition(definition, schedule));
  }

  public Optional<ScheduleSnapshot> snapshot(long definitionId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? Optional.empty() : manager.get(key(definitionId));
  }

  public List<ScheduleSnapshot> list() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? List.of() : manager.list(NAMESPACE);
  }

  public void pauseIfPresent(long definitionId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(definitionId);
    if (manager.get(key).isPresent()) manager.pause(key);
  }

  public void deleteIfPresent(long definitionId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(definitionId);
    if (manager.get(key).isPresent()) manager.delete(key);
  }

  public void runNowIfPresent(long definitionId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(definitionId);
    if (manager.get(key).isPresent()) manager.runNow(key);
  }

  ScheduleDefinition toDefinition(OfflineJobDefinition definition, OfflineSchedule schedule) {
    if (definition == null || definition.getId() == null || definition.getId() <= 0L) {
      throw new IllegalArgumentException("离线同步任务不能为空");
    }
    if (schedule == null || !StringUtils.hasText(schedule.cronExpression())) {
      throw new IllegalArgumentException("离线同步任务未配置 Cron 调度");
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("definitionId", definition.getId());

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("definitionId", String.valueOf(definition.getId()));
    put(metadata, "mode", definition.getMode());
    put(metadata, "sourceType", definition.getSourceType());
    put(metadata, "sinkType", definition.getSinkType());

    boolean enabled = "ONLINE".equalsIgnoreCase(definition.getReleaseState()) && schedule.enabled();
    return new ScheduleDefinition(
        key(definition.getId()),
        definition.getJobName(),
        ScheduleTrigger.cron(schedule.cronExpression(), ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        enabled,
        metadata);
  }

  private ScheduleKey key(long definitionId) {
    if (definitionId <= 0L) throw new IllegalArgumentException("离线同步任务 ID 不合法");
    return new ScheduleKey(NAMESPACE, String.valueOf(definitionId));
  }

  private ScheduleManager manager() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) {
      throw new IllegalStateException(
          "Yak ScheduleManager 不可用，请确认 yak-schedule-core、调度引擎插件与 yak.schedule.enabled 配置");
    }
    return manager;
  }

  private void put(Map<String, String> metadata, String key, String value) {
    if (StringUtils.hasText(value)) metadata.put(key, value);
  }
}
