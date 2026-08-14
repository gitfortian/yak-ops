package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleTriggerHandlerTest {
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowDefinitionService definitions;
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;
  @Mock private WorkflowScheduleLifecycle lifecycle;
  @Mock private WorkflowScheduleEngineBridge engine;
  @Mock private WorkflowScheduleRuntimeState runtimeState;

  private WorkflowScheduleTriggerHandler handler;

  @BeforeEach
  void setUp() {
    handler = new WorkflowScheduleTriggerHandler(
        schedules, definitions, coordinator, lifecycle, engine, runtimeState);
  }

  @Test
  void shouldRouteQuartzFireIntoDurableTriggerCoordinator() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");
    Instant actual = planned.plusSeconds(1);
    WorkflowSchedulePO schedule = schedule();
    WorkflowDefinitionVO workflow = org.mockito.Mockito.mock(WorkflowDefinitionVO.class);
    when(workflow.status()).thenReturn("ONLINE");
    when(workflow.activeVersionId()).thenReturn("workflow-version-1");
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(definitions.get("workflow-1")).thenReturn(workflow);
    when(coordinator.submit(schedule, "trigger-1", planned, actual, "CRON"))
        .thenReturn(ScheduleExecutionResult.accepted("execution-1"));
    when(engine.snapshot("schedule-1")).thenReturn(Optional.empty());

    ScheduleExecutionResult result = handler.execute(context(planned));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isEqualTo("execution-1");
    verify(coordinator).submit(schedule, "trigger-1", planned, actual, "CRON");
    verify(runtimeState).recordFire("schedule-1", actual, null);
  }

  @Test
  void shouldIgnoreFireBeforeScheduleStartTime() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");
    Instant actual = planned.plusSeconds(1);
    WorkflowSchedulePO schedule = schedule();
    schedule.setStartTime(Instant.parse("2026-08-15T00:00:00Z"));
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(engine.snapshot("schedule-1")).thenReturn(Optional.empty());

    ScheduleExecutionResult result = handler.execute(context(planned));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isNull();
    verify(coordinator, never()).submit(any(), any(), any(), any(), any());
    verify(runtimeState).recordFire("schedule-1", actual, null);
  }

  private WorkflowSchedulePO schedule() {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setStatus("ONLINE");
    return value;
  }

  private ScheduleExecutionContext context(Instant planned) {
    return new ScheduleExecutionContext(
        "trigger-1",
        new ScheduleKey(WorkflowScheduleEngineBridge.NAMESPACE, "schedule-1"),
        "quartz",
        WorkflowScheduleEngineBridge.HANDLER,
        Map.of("scheduleId", "schedule-1", "workflowId", "workflow-1"),
        planned,
        planned.plusSeconds(1),
        false,
        1);
  }
}
