package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Yak Schedule 工作流 Handler：校验调度窗口后统一进入 Trigger Ledger 协调器。 */
@Component(WorkflowScheduleEngineBridge.HANDLER)
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerHandler implements ScheduleHandler {
  private final WorkflowScheduleQuery schedules;
  private final WorkflowDefinitionService definitions;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final WorkflowScheduleLifecycle lifecycle;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;

  public WorkflowScheduleTriggerHandler(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionService definitions,
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowScheduleLifecycle lifecycle,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.coordinator = coordinator;
    this.lifecycle = lifecycle;
    this.engine = engine;
    this.runtimeState = runtimeState;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    String scheduleId = context.requiredString("scheduleId");
    WorkflowSchedulePO schedule;
    try {
      schedule = schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      engine.deleteIfPresent(scheduleId);
      return ScheduleExecutionResult.accepted(null, "调度定义已删除，清理残留引擎计划");
    }

    Instant plannedFireTime = context.scheduledFireTime() == null
        ? context.actualFireTime()
        : context.scheduledFireTime();
    Instant actualFireTime = context.actualFireTime();

    if (!"ONLINE".equals(schedule.getStatus())) {
      engine.pauseIfPresent(scheduleId);
      runtimeState.clearNext(scheduleId);
      return ScheduleExecutionResult.accepted(null, "调度已停用，本次触发忽略");
    }

    if (schedule.getStartTime() != null && plannedFireTime.isBefore(schedule.getStartTime())) {
      refreshFireState(schedule, actualFireTime);
      return ScheduleExecutionResult.accepted(null, "尚未进入调度生效时间，本次触发忽略");
    }

    if (schedule.getEndTime() != null && plannedFireTime.isAfter(schedule.getEndTime())) {
      lifecycle.expire(scheduleId, actualFireTime);
      return ScheduleExecutionResult.accepted(null, "调度已超过生效时间并自动停用");
    }

    WorkflowDefinitionVO workflow;
    try {
      workflow = definitions.get(schedule.getWorkflowId());
    } catch (IllegalArgumentException missing) {
      lifecycle.offline(scheduleId);
      return ScheduleExecutionResult.accepted(null, "工作流定义已删除，调度自动停用");
    }
    if (!"ONLINE".equals(workflow.status()) || workflow.activeVersionId() == null) {
      lifecycle.offline(scheduleId);
      return ScheduleExecutionResult.accepted(null, "工作流已下线，调度自动停用");
    }

    try {
      return coordinator.submit(
          schedule,
          context.triggerId(),
          plannedFireTime,
          actualFireTime,
          context.manual() ? "MANUAL" : "CRON");
    } finally {
      refreshFireState(schedule, actualFireTime);
    }
  }

  private void refreshFireState(WorkflowSchedulePO schedule, Instant actualFireTime) {
    Instant next = engine.snapshot(schedule.getId())
        .map(snapshot -> snapshot.nextFireTime())
        .orElse(null);
    runtimeState.recordFire(schedule.getId(), actualFireTime, next);
  }
}
