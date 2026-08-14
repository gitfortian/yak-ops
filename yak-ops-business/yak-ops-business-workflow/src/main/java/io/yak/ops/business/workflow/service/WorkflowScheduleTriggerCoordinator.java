package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowScheduleTriggerIdentity;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.service.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.service.WorkflowScheduleTriggerAdmission.AdmissionResult;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Trigger Ledger、幂等、Backfill、运维补跑与 WorkflowExecution 并发策略统一协调器。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerCoordinator {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleTriggerCoordinator.class);
  private static final String BUSINESS_DATE_RERUN = "BUSINESS_DATE_RERUN";

  private final WorkflowScheduleTriggerDao ledger;
  private final WorkflowScheduleQuery schedules;
  private final WorkflowLaunchService launchService;
  private final WorkflowScheduleTriggerAdmission admission;
  private final WorkflowBackfillQuery backfills;
  private final WorkflowScheduleParameterResolver parameters;

  @Autowired
  public WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLaunchService launchService,
      WorkflowScheduleTriggerAdmission admission,
      WorkflowBackfillQuery backfills,
      WorkflowScheduleParameterResolver parameters) {
    this.ledger = ledger;
    this.schedules = schedules;
    this.launchService = launchService;
    this.admission = admission;
    this.backfills = backfills;
    this.parameters = parameters;
  }

  /** Focused Stage 4 tests retain the original constructor shape. */
  WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLaunchService launchService,
      WorkflowScheduleTriggerAdmission admission) {
    this(ledger, schedules, launchService, admission, null, null);
  }

  public ScheduleExecutionResult submit(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    AdmissionResult result = admission.admitNew(
        schedule,
        newScheduleTrigger(schedule, triggerId, plannedFireTime, actualFireTime, triggerSource));
    return handleAdmission(result, "工作流调度 Trigger 已获得执行准入");
  }

  public ScheduleExecutionResult submitBackfill(
      WorkflowBackfillPO backfill,
      Occurrence occurrence) {
    AdmissionResult result = admission.admitNew(newBackfillTrigger(backfill, occurrence));
    return handleAdmission(
        result,
        isOperationalRerun(backfill)
            ? "businessDate 运维补跑 Trigger 已获得执行准入"
            : "Backfill Trigger 已获得执行准入");
  }

  private ScheduleExecutionResult handleAdmission(AdmissionResult result, String acceptedMessage) {
    if (result.duplicate()) return duplicateResult(result.trigger());
    if (!result.launchNow()) {
      return ScheduleExecutionResult.accepted(
          result.trigger().getWorkflowExecutionId(), result.trigger().getMessage());
    }
    String executionId = launchReserved(result);
    return ScheduleExecutionResult.accepted(executionId, acceptedMessage);
  }

  public ScheduleExecutionResult recoverMisfire(
      WorkflowSchedulePO schedule,
      Instant plannedFireTime,
      Instant actualFireTime) {
    String triggerId = "workflow-misfire-" + schedule.getId() + "-" + plannedFireTime.toEpochMilli();
    return submit(schedule, triggerId, plannedFireTime, actualFireTime, "MISFIRE_RECOVERY");
  }

  /** 应用重启后修复 Ledger 中间态，并恢复 SERIAL_WAIT / Backfill / 运维补跑队列。 */
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
        AdmissionResult recovered = admission.recoverBound(trigger, executionId);
        if (recovered.launchNow()) {
          launchReserved(recovered);
          continue;
        }
        if (recovered.trigger() != null && "RECEIVED".equals(recovered.trigger().getStatus())) {
          if (!runnable(recovered.trigger())) {
            admission.skip(recovered.trigger(), "原 WorkflowExecution 已不存在且触发来源不可继续，恢复时跳过");
          } else {
            AdmissionResult readmitted = admission.readmit(recovered.trigger());
            if (readmitted.launchNow()) launchReserved(readmitted);
            else if (readmitted.trigger() != null && "WAITING".equals(readmitted.trigger().getStatus())) {
              waitingWorkflows.add(trigger.getWorkflowId());
            }
          }
        }
        continue;
      }

      if (!runnable(trigger)) {
        admission.skip(trigger, "调度/Backfill/运维补跑已不可继续，启动恢复时跳过未启动 Trigger");
        continue;
      }
      if ("WAITING".equals(trigger.getStatus())) {
        waitingWorkflows.add(trigger.getWorkflowId());
        continue;
      }

      AdmissionResult readmitted = admission.readmit(trigger);
      if (readmitted.launchNow()) launchReserved(readmitted);
      else if (readmitted.trigger() != null && "WAITING".equals(readmitted.trigger().getStatus())) {
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
      if (!runnable(trigger)) {
        admission.skip(trigger, "获得执行准入后调度/Backfill/运维补跑已不可继续，本次 Trigger 跳过");
        current = admission.reserveNext(trigger.getWorkflowId());
        continue;
      }

      try {
        String executionId;
        if (isBackfill(trigger)) {
          WorkflowInstanceVO launched = launchBackfill(trigger);
          executionId = launched.id();
        } else {
          WorkflowDefinitionVO launched = launchSchedule(trigger);
          executionId = launched.latestExecutionId();
        }
        if (executionId == null || executionId.isBlank()) {
          throw new IllegalStateException("工作流启动成功但未返回 WorkflowExecution ID");
        }
        if (firstExecutionId == null) firstExecutionId = executionId;
        log.info(
            "[workflow-schedule-ledger] launched trigger={}, source={}, schedule={}, backfill={}, workflow={}, execution={}, strategy={}, businessDate={}",
            trigger.getId(),
            trigger.getTriggerSource(),
            trigger.getScheduleId(),
            trigger.getBackfillId(),
            trigger.getWorkflowId(),
            executionId,
            trigger.getExecutionStrategy(),
            trigger.getBusinessDate());
        current = admission.bindLaunch(trigger, executionId);
      } catch (RuntimeException exception) {
        if (firstFailure == null) firstFailure = exception;
        log.warn(
            "[workflow-schedule-ledger] launch failed trigger={}, schedule={}, backfill={}, workflow={}, message={}",
            trigger.getId(),
            trigger.getScheduleId(),
            trigger.getBackfillId(),
            trigger.getWorkflowId(),
            exception.getMessage());
        current = admission.failLaunch(trigger, exception);
      }
    }

    if (firstFailure != null) throw firstFailure;
    return firstExecutionId;
  }

  private WorkflowDefinitionVO launchSchedule(WorkflowScheduleTriggerPO trigger) {
    WorkflowSchedulePO schedule = schedules.require(trigger.getScheduleId());
    WorkflowTriggerContext context = WorkflowTriggerContext.scheduled(
        trigger.getTriggerId(),
        schedule.getId(),
        trigger.getPlannedFireTime(),
        schedule.getTimezone());
    Map<String, Object> input = parameters == null ? Map.of() : parameters.forSchedule(schedule, context);
    return launchService.runScheduledPublished(schedule.getWorkflowId(), context, input);
  }

  private WorkflowInstanceVO launchBackfill(WorkflowScheduleTriggerPO trigger) {
    if (backfills == null) throw new IllegalStateException("Backfill 查询服务不可用");
    WorkflowBackfillPO backfill = backfills.require(trigger.getBackfillId());
    boolean operational = isOperationalRerun(backfill);
    WorkflowTriggerContext context = operational
        ? WorkflowTriggerContext.rerun(
            trigger.getTriggerId(),
            backfill.getScheduleId(),
            backfill.getId(),
            trigger.getPlannedFireTime(),
            backfill.getTimezone())
        : WorkflowTriggerContext.backfill(
            trigger.getTriggerId(),
            backfill.getScheduleId(),
            backfill.getId(),
            trigger.getPlannedFireTime(),
            backfill.getTimezone());
    Map<String, Object> input = parameters == null ? Map.of() : parameters.forBackfill(backfill, context);
    return operational
        ? launchService.runOperationalPublished(
            backfill.getWorkflowId(), backfill.getWorkflowVersionId(), context, input)
        : launchService.runBackfillPublished(
            backfill.getWorkflowId(), backfill.getWorkflowVersionId(), context, input);
  }

  private WorkflowScheduleTriggerPO newScheduleTrigger(
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
    value.setDedupeKey(WorkflowScheduleTriggerIdentity.scheduled(schedule.getId(), plannedFireTime));
    value.setTriggerSource(triggerSource == null || triggerSource.isBlank() ? "CRON" : triggerSource.trim());
    value.setPlannedFireTime(plannedFireTime);
    value.setActualFireTime(actual);
    value.setBusinessDate(plannedFireTime.atZone(ZoneId.of(schedule.getTimezone())).toLocalDate());
    value.setExecutionStrategy(schedule.getExecutionStrategy());
    value.setMisfireStrategy(schedule.getMisfireStrategy());
    value.setStatus("RECEIVED");
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return value;
  }

  private WorkflowScheduleTriggerPO newBackfillTrigger(
      WorkflowBackfillPO backfill,
      Occurrence occurrence) {
    if (backfill == null || occurrence == null) throw new IllegalArgumentException("Backfill Trigger 参数不能为空");
    Instant planned = occurrence.scheduleInstant();
    Instant now = Instant.now();
    boolean operational = isOperationalRerun(backfill);
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("workflow-trigger-ledger-" + UUID.randomUUID());
    value.setScheduleId(backfill.getScheduleId());
    value.setWorkflowId(backfill.getWorkflowId());
    value.setBackfillId(backfill.getId());
    value.setTriggerId((operational ? "workflow-rerun-" : "workflow-backfill-")
        + backfill.getId() + "-" + planned.toEpochMilli());
    value.setDedupeKey(WorkflowScheduleTriggerIdentity.backfill(
        backfill.getScheduleId(), backfill.getId(), planned));
    value.setTriggerSource(operational ? BUSINESS_DATE_RERUN : "BACKFILL");
    value.setPlannedFireTime(planned);
    value.setActualFireTime(now);
    value.setBusinessDate(occurrence.businessDate());
    value.setExecutionStrategy(backfill.getExecutionStrategy());
    value.setMisfireStrategy("FIRE_ONCE");
    value.setStatus("RECEIVED");
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return value;
  }

  private ScheduleExecutionResult duplicateResult(WorkflowScheduleTriggerPO trigger) {
    return ScheduleExecutionResult.accepted(
        trigger.getWorkflowExecutionId(),
        "重复计划触发已由 Trigger Ledger 幂等拦截，复用状态 " + trigger.getStatus());
  }

  private boolean runnable(WorkflowScheduleTriggerPO trigger) {
    if (isBackfill(trigger)) {
      WorkflowBackfillPO value = safeBackfill(trigger.getBackfillId());
      return value != null && !"CANCELED".equals(value.getStatus());
    }
    WorkflowSchedulePO schedule = safeSchedule(trigger.getScheduleId());
    return schedule != null && "ONLINE".equals(schedule.getStatus());
  }

  private boolean isBackfill(WorkflowScheduleTriggerPO trigger) {
    return trigger.getBackfillId() != null && !trigger.getBackfillId().isBlank();
  }

  private boolean isOperationalRerun(WorkflowBackfillPO backfill) {
    return backfill != null && BUSINESS_DATE_RERUN.equals(backfill.getOperationType());
  }

  private WorkflowSchedulePO safeSchedule(String scheduleId) {
    try {
      return schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private WorkflowBackfillPO safeBackfill(String backfillId) {
    if (backfills == null) return null;
    try {
      return backfills.require(backfillId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
