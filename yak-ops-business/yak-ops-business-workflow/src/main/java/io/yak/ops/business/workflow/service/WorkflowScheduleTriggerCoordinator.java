package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.service.WorkflowScheduleTriggerAdmission.AdmissionResult;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Trigger Ledger、幂等与 WorkflowExecution 并发策略的统一协调器。
 *
 * <p>准入由短事务完成；真正创建 WorkflowExecution 时不持有工作流行锁。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerCoordinator {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleTriggerCoordinator.class);

  private final WorkflowScheduleTriggerDao ledger;
  private final WorkflowScheduleQuery schedules;
  private final WorkflowLaunchService launchService;
  private final WorkflowScheduleTriggerAdmission admission;

  public WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLaunchService launchService,
      WorkflowScheduleTriggerAdmission admission) {
    this.ledger = ledger;
    this.schedules = schedules;
    this.launchService = launchService;
    this.admission = admission;
  }

  public ScheduleExecutionResult submit(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    AdmissionResult result = admission.admitNew(
        schedule,
        newTrigger(schedule, triggerId, plannedFireTime, actualFireTime, triggerSource));
    if (result.duplicate()) return duplicateResult(result.trigger());
    if (!result.launchNow()) {
      return ScheduleExecutionResult.accepted(
          result.trigger().getWorkflowExecutionId(),
          result.trigger().getMessage());
    }

    String executionId = launchReserved(result);
    return ScheduleExecutionResult.accepted(executionId, "工作流调度 Trigger 已获得执行准入");
  }

  public ScheduleExecutionResult recoverMisfire(
      WorkflowSchedulePO schedule,
      Instant plannedFireTime,
      Instant actualFireTime) {
    String triggerId = "workflow-misfire-" + schedule.getId() + "-" + plannedFireTime.toEpochMilli();
    return submit(
        schedule,
        triggerId,
        plannedFireTime,
        actualFireTime,
        "MISFIRE_RECOVERY");
  }

  /** 应用重启后修复 Ledger 中间态，并恢复 SERIAL_WAIT 队列。 */
  public void recoverPending() {
    List<WorkflowScheduleTriggerPO> pending = ledger.selectPending();
    Set<String> waitingWorkflows = new LinkedHashSet<>();

    for (WorkflowScheduleTriggerPO trigger : pending) {
      String executionId = trigger.getWorkflowExecutionId();
      if ((executionId == null || executionId.isBlank())
          && ("LAUNCHING".equals(trigger.getStatus()) || "RUNNING".equals(trigger.getStatus()))) {
        executionId = ledger.selectExecutionIdByTrigger(trigger.getTriggerId());
      }

      if (executionId != null && !executionId.isBlank()) {
        AdmissionResult next = admission.recoverBound(trigger, executionId);
        launchReserved(next);
        continue;
      }

      WorkflowSchedulePO schedule = safeSchedule(trigger.getScheduleId());
      if (schedule == null || !"ONLINE".equals(schedule.getStatus())) {
        admission.skip(trigger, "调度已停用或删除，启动恢复时跳过未启动 Trigger");
        continue;
      }

      if ("WAITING".equals(trigger.getStatus())) {
        waitingWorkflows.add(trigger.getWorkflowId());
        continue;
      }

      AdmissionResult readmitted = admission.readmit(schedule, trigger);
      if (readmitted.launchNow()) {
        launchReserved(readmitted);
      } else if ("WAITING".equals(readmitted.trigger().getStatus())) {
        waitingWorkflows.add(trigger.getWorkflowId());
      }
    }

    for (String workflowId : waitingWorkflows) {
      launchReserved(admission.reserveNext(workflowId));
    }
  }

  /** WorkflowExecution 终态事务提交后调用，完成 Ledger 并推进 SERIAL_WAIT。 */
  public void completeExecution(String executionId, String executionStatus, Instant endedAt) {
    launchReserved(admission.completeExecution(executionId, executionStatus, endedAt));
  }

  private String launchReserved(AdmissionResult initial) {
    AdmissionResult current = initial;
    String firstExecutionId = null;
    RuntimeException firstFailure = null;

    while (current != null && current.launchNow() && current.trigger() != null) {
      WorkflowScheduleTriggerPO trigger = current.trigger();
      WorkflowSchedulePO schedule = safeSchedule(trigger.getScheduleId());
      if (schedule == null || !"ONLINE".equals(schedule.getStatus())) {
        admission.skip(trigger, "获得执行准入后调度已停用或删除，本次 Trigger 跳过");
        current = admission.reserveNext(trigger.getWorkflowId());
        continue;
      }

      try {
        WorkflowDefinitionVO launched = launchService.runScheduledPublished(
            schedule.getWorkflowId(),
            WorkflowTriggerContext.scheduled(
                trigger.getTriggerId(),
                schedule.getId(),
                trigger.getPlannedFireTime()));
        String executionId = launched.latestExecutionId();
        if (firstExecutionId == null) firstExecutionId = executionId;
        log.info(
            "[workflow-schedule-ledger] launched trigger={}, schedule={}, workflow={}, execution={}, strategy={}",
            trigger.getId(),
            schedule.getId(),
            schedule.getWorkflowId(),
            executionId,
            schedule.getExecutionStrategy());
        current = admission.bindLaunch(trigger, executionId);
      } catch (RuntimeException exception) {
        if (firstFailure == null) firstFailure = exception;
        log.warn(
            "[workflow-schedule-ledger] launch failed trigger={}, schedule={}, workflow={}, message={}",
            trigger.getId(),
            trigger.getScheduleId(),
            trigger.getWorkflowId(),
            exception.getMessage());
        current = admission.failLaunch(trigger, exception);
      }
    }

    if (firstFailure != null) throw firstFailure;
    return firstExecutionId;
  }

  private WorkflowScheduleTriggerPO newTrigger(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    if (schedule == null) throw new IllegalArgumentException("调度定义不能为空");
    if (plannedFireTime == null) throw new IllegalArgumentException("plannedFireTime 不能为空");
    Instant actual = actualFireTime == null ? Instant.now() : actualFireTime;
    Instant now = Instant.now();
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("workflow-trigger-ledger-" + UUID.randomUUID());
    value.setScheduleId(schedule.getId());
    value.setWorkflowId(schedule.getWorkflowId());
    value.setTriggerId(required(triggerId, "triggerId 不能为空"));
    value.setTriggerSource(triggerSource == null || triggerSource.isBlank() ? "CRON" : triggerSource.trim());
    value.setPlannedFireTime(plannedFireTime);
    value.setActualFireTime(actual);
    value.setExecutionStrategy(schedule.getExecutionStrategy());
    value.setMisfireStrategy(schedule.getMisfireStrategy());
    value.setStatus("RECEIVED");
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return value;
  }

  private ScheduleExecutionResult duplicateResult(WorkflowScheduleTriggerPO trigger) {
    String message = "重复计划触发已由 Trigger Ledger 幂等拦截，复用状态 " + trigger.getStatus();
    return ScheduleExecutionResult.accepted(trigger.getWorkflowExecutionId(), message);
  }

  private WorkflowSchedulePO safeSchedule(String scheduleId) {
    try {
      return schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
