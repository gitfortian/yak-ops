package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Yak Schedule 的工作流业务 Handler：只负责把时间触发转换成统一 Workflow Launch。 */
@Component(WorkflowScheduleEngineBridge.HANDLER)
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerHandler implements ScheduleHandler {
  private final WorkflowScheduleQuery schedules;
  private final WorkflowDefinitionService definitions;
  private final WorkflowLaunchService launchService;
  private final WorkflowScheduleLifecycle lifecycle;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;

  public WorkflowScheduleTriggerHandler(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionService definitions,
      WorkflowLaunchService launchService,
      WorkflowScheduleLifecycle lifecycle,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.launchService = launchService;
    this.lifecycle = lifecycle;
    this.engine = engine;
    this.runtimeState = runtimeState;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    String scheduleId = context.requiredString("scheduleId");
    WorkflowSchedulePO schedule = schedules.require(scheduleId);
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

    WorkflowDefinitionVO workflow = definitions.get(schedule.getWorkflowId());
    if (!"ONLINE".equals(workflow.status()) || workflow.activeVersionId() == null) {
      lifecycle.offline(scheduleId);
      return ScheduleExecutionResult.accepted(null, "工作流已下线，调度自动停用");
    }

    try {
      WorkflowDefinitionVO launched = launchService.runPublished(
          schedule.getWorkflowId(),
          WorkflowTriggerContext.scheduled(
              context.triggerId(),
              scheduleId,
              plannedFireTime));
      return ScheduleExecutionResult.accepted(
          launched.latestExecutionId(),
          "工作流调度触发已提交");
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
