package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleTriggerAdmissionTest {
  @Mock private WorkflowScheduleTriggerDao ledger;
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowExecutionDao executions;

  private WorkflowScheduleTriggerAdmission admission;

  @BeforeEach
  void setUp() {
    admission = new WorkflowScheduleTriggerAdmission(ledger, schedules, executions);
  }

  @Test
  void shouldReturnExistingLedgerWithoutSecondAdmission() {
    WorkflowSchedulePO schedule = schedule("SERIAL_WAIT");
    WorkflowScheduleTriggerPO existing = trigger("RUNNING");
    existing.setWorkflowExecutionId("execution-1");
    when(ledger.claim(existing)).thenReturn(existing);

    var result = admission.admitNew(schedule, existing);

    assertThat(result.duplicate()).isTrue();
    assertThat(result.launchNow()).isFalse();
    assertThat(result.trigger().getWorkflowExecutionId()).isEqualTo("execution-1");
    verify(ledger, never()).lockWorkflow("workflow-1");
  }

  @Test
  void shouldQueueSerialWaitWhenWorkflowIsBusy() {
    WorkflowSchedulePO schedule = schedule("SERIAL_WAIT");
    WorkflowScheduleTriggerPO trigger = trigger("RECEIVED");
    when(ledger.claim(trigger)).thenReturn(trigger);
    when(ledger.countActiveExecutions("workflow-1")).thenReturn(1L);
    when(ledger.countLaunchingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.countWaitingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.update(trigger)).thenReturn(1);

    var result = admission.admitNew(schedule, trigger);

    assertThat(result.launchNow()).isFalse();
    assertThat(trigger.getStatus()).isEqualTo("WAITING");
    assertThat(trigger.getMessage()).contains("串行等待");
  }

  @Test
  void shouldNotLetNewSerialWaitJumpAheadOfExistingBacklog() {
    WorkflowSchedulePO schedule = schedule("SERIAL_WAIT");
    WorkflowScheduleTriggerPO trigger = trigger("RECEIVED");
    when(ledger.claim(trigger)).thenReturn(trigger);
    when(ledger.countActiveExecutions("workflow-1")).thenReturn(0L);
    when(ledger.countLaunchingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.countWaitingTriggers("workflow-1")).thenReturn(2L);
    when(ledger.update(trigger)).thenReturn(1);

    var result = admission.admitNew(schedule, trigger);

    assertThat(result.launchNow()).isFalse();
    assertThat(trigger.getStatus()).isEqualTo("WAITING");
    assertThat(trigger.getMessage()).contains("排队");
  }

  @Test
  void shouldSkipSerialDiscardWhenWorkflowIsBusy() {
    WorkflowSchedulePO schedule = schedule("SERIAL_DISCARD");
    WorkflowScheduleTriggerPO trigger = trigger("RECEIVED");
    when(ledger.claim(trigger)).thenReturn(trigger);
    when(ledger.countActiveExecutions("workflow-1")).thenReturn(1L);
    when(ledger.countLaunchingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.countWaitingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.update(trigger)).thenReturn(1);

    var result = admission.admitNew(schedule, trigger);

    assertThat(result.launchNow()).isFalse();
    assertThat(trigger.getStatus()).isEqualTo("SKIPPED");
    assertThat(trigger.getCompletedAt()).isNotNull();
  }

  @Test
  void shouldReserveParallelLaunchEvenWhenWorkflowIsBusy() {
    WorkflowSchedulePO schedule = schedule("PARALLEL");
    WorkflowScheduleTriggerPO trigger = trigger("RECEIVED");
    when(ledger.claim(trigger)).thenReturn(trigger);
    when(ledger.countActiveExecutions("workflow-1")).thenReturn(3L);
    when(ledger.countLaunchingTriggers("workflow-1")).thenReturn(2L);
    when(ledger.countWaitingTriggers("workflow-1")).thenReturn(4L);
    when(ledger.update(trigger)).thenReturn(1);

    var result = admission.admitNew(schedule, trigger);

    assertThat(result.launchNow()).isTrue();
    assertThat(trigger.getStatus()).isEqualTo("LAUNCHING");
    assertThat(trigger.getLaunchedAt()).isNotNull();
  }

  @Test
  void shouldReserveOldestWaitingTriggerAfterExecutionCompletes() {
    WorkflowScheduleTriggerPO completed = trigger("RUNNING");
    completed.setWorkflowExecutionId("execution-1");
    WorkflowScheduleTriggerPO waiting = trigger("WAITING");
    waiting.setId("ledger-2");
    waiting.setScheduleId("schedule-2");
    waiting.setPlannedFireTime(Instant.parse("2026-08-14T03:00:00Z"));
    WorkflowSchedulePO waitingSchedule = schedule("SERIAL_WAIT");
    waitingSchedule.setId("schedule-2");

    when(ledger.selectByExecutionId("execution-1")).thenReturn(completed, completed);
    when(ledger.update(completed)).thenReturn(1);
    when(ledger.countActiveExecutions("workflow-1")).thenReturn(0L);
    when(ledger.countLaunchingTriggers("workflow-1")).thenReturn(0L);
    when(ledger.selectNextWaiting("workflow-1")).thenReturn(waiting);
    when(schedules.require("schedule-2")).thenReturn(waitingSchedule);
    when(ledger.update(waiting)).thenReturn(1);

    var result = admission.completeExecution(
        "execution-1", "SUCCESS", Instant.parse("2026-08-14T02:30:00Z"));

    assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(result.launchNow()).isTrue();
    assertThat(result.trigger()).isSameAs(waiting);
    assertThat(waiting.getStatus()).isEqualTo("LAUNCHING");
  }

  private WorkflowSchedulePO schedule(String strategy) {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setStatus("ONLINE");
    value.setExecutionStrategy(strategy);
    value.setMisfireStrategy("FIRE_ONCE");
    return value;
  }

  private WorkflowScheduleTriggerPO trigger(String status) {
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("ledger-1");
    value.setScheduleId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setTriggerId("trigger-1");
    value.setPlannedFireTime(Instant.parse("2026-08-14T02:00:00Z"));
    value.setActualFireTime(Instant.parse("2026-08-14T02:00:01Z"));
    value.setExecutionStrategy("SERIAL_WAIT");
    value.setMisfireStrategy("FIRE_ONCE");
    value.setStatus(status);
    value.setCreateTime(Instant.parse("2026-08-14T02:00:01Z"));
    value.setUpdateTime(Instant.parse("2026-08-14T02:00:01Z"));
    return value;
  }
}
