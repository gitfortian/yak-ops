package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Yak Ops Workflow Schedule 到 Yak Framework ScheduleManager 的适配层。
 *
 * <p>业务表是调度定义的事实来源；Yak Schedule 只负责时间触发和引擎路由。</p>
 */
@Component
public class WorkflowScheduleEngineBridge {
  static final String NAMESPACE = "yak-ops-workflow";
  static final String HANDLER = "workflowScheduleHandler";

  private final ObjectProvider<ScheduleManager> scheduleManagers;

  public WorkflowScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.scheduleManagers = scheduleManagers;
  }

  public boolean available() {
    return scheduleManagers.getIfAvailable() != null;
  }

  public ScheduleSnapshot save(WorkflowSchedulePO schedule) {
    return manager().save(toDefinition(schedule));
  }

  public Optional<ScheduleSnapshot> snapshot(String scheduleId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? Optional.empty() : manager.get(key(scheduleId));
  }

  public List<ScheduleSnapshot> list() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    return manager == null ? List.of() : manager.list(NAMESPACE);
  }

  public void pauseIfPresent(String scheduleId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(scheduleId);
    if (manager.get(key).isPresent()) manager.pause(key);
  }

  public void deleteIfPresent(String scheduleId) {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) return;
    ScheduleKey key = key(scheduleId);
    if (manager.get(key).isPresent()) manager.delete(key);
  }

  ScheduleDefinition toDefinition(WorkflowSchedulePO schedule) {
    if (schedule == null) throw new IllegalArgumentException("工作流调度不能为空");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scheduleId", schedule.getId());
    payload.put("workflowId", schedule.getWorkflowId());

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("workflowId", schedule.getWorkflowId());
    metadata.put("scheduleId", schedule.getId());
    if (schedule.getStartTime() != null) metadata.put("startTime", schedule.getStartTime().toString());
    if (schedule.getEndTime() != null) metadata.put("endTime", schedule.getEndTime().toString());

    return new ScheduleDefinition(
        key(schedule.getId()),
        schedule.getName(),
        ScheduleTrigger.cron(schedule.getCronExpression(), ZoneId.of(schedule.getTimezone())),
        new ScheduleTarget(HANDLER, payload),
        new SchedulePolicy(
            concurrency(schedule.getExecutionStrategy()),
            misfire(schedule.getMisfireStrategy()),
            0),
        "ONLINE".equals(schedule.getStatus()),
        metadata);
  }

  ScheduleKey key(String scheduleId) {
    if (scheduleId == null || scheduleId.isBlank()) {
      throw new IllegalArgumentException("调度 ID 不能为空");
    }
    return new ScheduleKey(NAMESPACE, scheduleId.trim());
  }

  private ScheduleManager manager() {
    ScheduleManager manager = scheduleManagers.getIfAvailable();
    if (manager == null) {
      throw new IllegalStateException(
          "Yak ScheduleManager 不可用，请确认 yak-schedule-core、Quartz 插件与 yak.schedule.enabled 配置");
    }
    return manager;
  }

  private ConcurrencyPolicy concurrency(String strategy) {
    return "PARALLEL".equals(strategy) ? ConcurrencyPolicy.ALLOW : ConcurrencyPolicy.FORBID;
  }

  private MisfirePolicy misfire(String strategy) {
    return "SKIP".equals(strategy) ? MisfirePolicy.IGNORE : MisfirePolicy.FIRE_ONCE_NOW;
  }
}
