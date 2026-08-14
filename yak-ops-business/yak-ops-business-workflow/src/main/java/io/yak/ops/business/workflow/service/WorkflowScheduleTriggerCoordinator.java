package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Trigger Ledger、幂等与 WorkflowExecution 并发策略的统一协调器。
 *
 * <p>Yak Schedule 只负责“到点通知”；是否立即创建 WorkflowExecution 由这里决定。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerCoordinator {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleTriggerCoordinator.class);
  private static final Set<String> TERMINAL = Set.of(
      "SUCCESS", "SUCCESS_WITH_WARNINGS", "FAILED", "CANCELED", "TIMED_OUT");

  private final WorkflowScheduleTriggerDao ledger;
  private final WorkflowScheduleQuery schedules;
  private final WorkflowLaunchService launchService;
  private final WorkflowExecutionDao executions;

  public WorkflowScheduleTriggerCoordinator(
      WorkflowScheduleTriggerDao ledger,
      WorkflowScheduleQuery schedules,
      WorkflowLaunchService launchService,
      WorkflowExecutionDao executions) {
    this.ledger = ledger;
    this.schedules = schedules;
    this.launchService = launchService;
    this.executions = executions;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public ScheduleExecutionResult submit(
      WorkflowSchedulePO schedule,
      String triggerId,
      Instant plannedFireTime,
      Instant actualFireTime,
      String triggerSource) {
    WorkflowScheduleTriggerPO claimed = ledger.claim(newTrigger(
        schedule,
        triggerId,
        plannedFireTime,
        actualFireTime,
        triggerSource));

    if (!"RECEIVED".equals(claimed.getStatus())) {
      return duplicateResult(claimed);
    }

    ledger.lockWorkflow(schedule.getWorkflowId());
    return decideAndLaunch(schedule, claimed);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public ScheduleExecutionResult recoverMisfire(
      WorkflowSchedulePO schedule,
      Instant plannedFireTime,
      Instant actualFireTime) {
    String triggerId = "workflow-misfire-" + schedule.getId() + "-" + plannedFireTime.toEpochMilli();
    WorkflowScheduleTriggerPO claimed = ledger.claim(newTrigger(
        schedule,
        triggerId,
        plannedFireTime,
        actualFireTime,
        "MISFIRE_RECOVERY"));
    if (!"RECEIVED".equals(claimed.getStatus())) return duplicateResult(claimed);
    ledger.lockWorkflow(schedule.getWorkflowId());
    return decideAndLaunch(schedule, claimed);
  }

  /** 应用重启后修复 Ledger 中间态，并继续可恢复的 Trigger。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void recoverPending() {
    List<WorkflowScheduleTriggerPO> pending = ledger.selectPending();
    Set<String> workflowsToDrain = new LinkedHashSet<>();

    for (WorkflowScheduleTriggerPO trigger : pending) {
      WorkflowSchedulePO schedule = safeSchedule(trigger.getScheduleId());
      if (schedule == null || !"ONLINE".equals(schedule.getStatus())) {
        markSkipped(trigger, "调度已停用或删除，启动恢复时跳过");
        continue;
      }

      if ("RUNNING".equals(trigger.getStatus()) && trigger.getWorkflowExecutionId() != null) {
        WorkflowExecutionPO execution = executions.selectExecution(trigger.getWorkflowExecutionId());
        if (execution != null && isTerminal(execution.getStatus())) {
          markTerminal(trigger, execution.getStatus(), execution.getEndedAt());
        } else {
          workflowsToDrain.add(trigger.getWorkflowId());
        }
        continue;
      }

      if ("LAUNCHING".equals(trigger.getStatus()) || "RUNNING".equals(trigger.getStatus())) {
        String executionId = trigger.getWorkflowExecutionId();
        if (executionId == null || executionId.isBlank()) {
          executionId = ledger.selectExecutionIdByTrigger(trigger.getTriggerId());
        }
        if (executionId != null && !executionId.isBlank()) {
          trigger.setWorkflowExecutionId(executionId);
          WorkflowExecutionPO execution = executions.selectExecution(executionId);
          if (execution != null && isTerminal(execution.getStatus())) {
            markTerminal(trigger, execution.getStatus(), execution.getEndedAt());
          } else {
            trigger.setStatus("RUNNING");
            trigger.setExecutionStatus(execution == null ? null : execution.getStatus());
            trigger.setMessage("启动恢复已重新绑定 WorkflowExecution");
            touch(trigger);
            save(trigger);
          }
          workflowsToDrain.add(trigger.getWorkflowId());
          continue;
        }
        trigger.setStatus("RECEIVED");
        trigger.setMessage("启动恢复未发现已创建实例，重新执行准入判断");
        touch(trigger);
        save(trigger);
      }

      if ("RECEIVED".equals(trigger.getStatus())) {
        ledger.lockWorkflow(trigger.getWorkflowId());
        decideAndLaunch(schedule, trigger);
      } else if ("WAITING".equals(trigger.getStatus())) {
        workflowsToDrain.add(trigger.getWorkflowId());
      }
    }

    for (String workflowId : workflowsToDrain) {
      ledger.lockWorkflow(workflowId);
      drainWaitingLocked(workflowId);
    }
  }

  /** WorkflowExecution 终态事务提交后调用，完成 Ledger 并推进等待队列。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void completeExecution(String executionId, String executionStatus, Instant endedAt) {
    WorkflowScheduleTriggerPO trigger = ledger.selectByExecutionId(executionId);
    if (trigger != null && !isLedgerTerminal(trigger.getStatus())) {
      markTerminal(trigger, executionStatus, endedAt);
    }

    String workflowId = trigger == null
        ? ledger.selectWorkflowIdByExecution(executionId)
        : trigger.getWorkflowId();
    if (workflowId == null || workflowId.isBlank()) return;

    ledger.lockWorkflow(workflowId);
    drainWaitingLocked(workflowId);
  }

  private ScheduleExecutionResult decideAndLaunch(
      WorkflowSchedulePO schedule,
      WorkflowScheduleTriggerPO trigger) {
    String strategy = schedule.getExecutionStrategy();
    long active = ledger.countActiveExecutions(schedule.getWorkflowId());

    if ("SERIAL_DISCARD".equals(strategy) && active > 0L) {
      markSkipped(trigger, "已有运行中的 WorkflowExecution，按 SERIAL_DISCARD 跳过");
      return ScheduleExecutionResult.accepted(null, trigger.getMessage());
    }

    if ("SERIAL_WAIT".equals(strategy) && active > 0L) {
      trigger.setStatus("WAITING");
      trigger.setMessage("已有运行中的 WorkflowExecution，进入串行等待队列");
      touch(trigger);
      save(trigger);
      return ScheduleExecutionResult.accepted(null, trigger.getMessage());
    }

    return launch(schedule, trigger);
  }

  private ScheduleExecutionResult launch(
      WorkflowSchedulePO schedule,
      WorkflowScheduleTriggerPO trigger) {
    trigger.setStatus("LAUNCHING");
    trigger.setMessage("Trigger 已获得执行准入，正在创建 WorkflowExecution");
    trigger.setLaunchedAt(Instant.now());
    touch(trigger);
    save(trigger);

    try {
      WorkflowDefinitionVO launched = launchService.runScheduledPublished(
          schedule.getWorkflowId(),
          WorkflowTriggerContext.scheduled(
              trigger.getTriggerId(),
              schedule.getId(),
              trigger.getPlannedFireTime()));
      String executionId = launched.latestExecutionId();
      trigger.setWorkflowExecutionId(executionId);
      trigger.setExecutionStatus(launched.latestExecutionStatus());

      WorkflowExecutionPO execution = executionId == null ? null : executions.selectExecution(executionId);
      String executionStatus = execution == null
          ? launched.latestExecutionStatus()
          : execution.getStatus();
      if (isTerminal(executionStatus)) {
        markTerminal(trigger, executionStatus, execution == null ? Instant.now() : execution.getEndedAt());
      } else {
        trigger.setStatus("RUNNING");
        trigger.setExecutionStatus(executionStatus);
        trigger.setMessage("WorkflowExecution 已创建");
        touch(trigger);
        save(trigger);
      }

      log.info(
          "[workflow-schedule-ledger] launched trigger={}, schedule={}, workflow={}, execution={}, strategy={}",
          trigger.getId(), schedule.getId(), schedule.getWorkflowId(), executionId, schedule.getExecutionStrategy());
      return ScheduleExecutionResult.accepted(executionId, trigger.getMessage());
    } catch (RuntimeException exception) {
      trigger.setStatus("FAILED");
      trigger.setErrorMessage(safeMessage(exception));
      trigger.setMessage("创建 WorkflowExecution 失败");
      trigger.setCompletedAt(Instant.now());
      touch(trigger);
      save(trigger);
      throw exception;
    }
  }

  private void drainWaitingLocked(String workflowId) {
    if (ledger.countActiveExecutions(workflowId) > 0L) return;

    while (true) {
      WorkflowScheduleTriggerPO waiting = ledger.selectNextWaiting(workflowId);
      if (waiting == null) return;
      WorkflowSchedulePO schedule = safeSchedule(waiting.getScheduleId());
      if (schedule == null || !"ONLINE".equals(schedule.getStatus())) {
        markSkipped(waiting, "等待期间调度已停用或删除");
        continue;
      }
      if (!"SERIAL_WAIT".equals(schedule.getExecutionStrategy())) {
        markSkipped(waiting, "等待期间执行策略已变更，旧 Trigger 不再推进");
        continue;
      }
      launch(schedule, waiting);
      return;
    }
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

  private void markSkipped(WorkflowScheduleTriggerPO trigger, String message) {
    trigger.setStatus("SKIPPED");
    trigger.setMessage(message);
    trigger.setCompletedAt(Instant.now());
    touch(trigger);
    save(trigger);
  }

  private void markTerminal(
      WorkflowScheduleTriggerPO trigger,
      String executionStatus,
      Instant endedAt) {
    trigger.setExecutionStatus(executionStatus);
    trigger.setStatus(ledgerStatus(executionStatus));
    trigger.setMessage("WorkflowExecution 已进入终态：" + executionStatus);
    trigger.setCompletedAt(endedAt == null ? Instant.now() : endedAt);
    touch(trigger);
    save(trigger);
  }

  private String ledgerStatus(String executionStatus) {
    if ("SUCCESS".equals(executionStatus) || "SUCCESS_WITH_WARNINGS".equals(executionStatus)) {
      return "SUCCEEDED";
    }
    if ("CANCELED".equals(executionStatus)) return "CANCELED";
    return "FAILED";
  }

  private boolean isTerminal(String status) {
    return status != null && TERMINAL.contains(status);
  }

  private boolean isLedgerTerminal(String status) {
    return List.of("SUCCEEDED", "FAILED", "CANCELED", "SKIPPED").contains(status);
  }

  private WorkflowSchedulePO safeSchedule(String scheduleId) {
    try {
      return schedules.require(scheduleId);
    } catch (IllegalArgumentException missing) {
      return null;
    }
  }

  private void touch(WorkflowScheduleTriggerPO trigger) {
    trigger.setUpdateTime(Instant.now());
  }

  private void save(WorkflowScheduleTriggerPO trigger) {
    if (ledger.update(trigger) != 1) {
      throw new IllegalStateException("更新 Trigger Ledger 失败：" + trigger.getId());
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private String safeMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) current = current.getCause();
    String message = current.getMessage();
    String value = message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : current.getClass().getSimpleName() + ": " + message;
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }
}
