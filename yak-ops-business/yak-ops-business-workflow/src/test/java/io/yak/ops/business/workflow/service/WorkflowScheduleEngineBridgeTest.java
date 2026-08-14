package io.yak.ops.business.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class WorkflowScheduleEngineBridgeTest {

  @Test
  void shouldMapWorkflowScheduleToYakScheduleDefinition() {
    ScheduleManager manager = mock(ScheduleManager.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(manager);
    when(manager.save(any())).thenAnswer(invocation -> {
      ScheduleDefinition definition = invocation.getArgument(0);
      return new ScheduleSnapshot(
          definition,
          "quartz",
          "yak-ops-workflow/schedule-1",
          ScheduleStatus.ENABLED,
          Instant.parse("2026-08-15T02:00:00Z"),
          null);
    });

    WorkflowScheduleEngineBridge bridge = new WorkflowScheduleEngineBridge(provider);
    WorkflowSchedulePO schedule = schedule("SERIAL_WAIT", "FIRE_ONCE");

    bridge.save(schedule);

    ArgumentCaptor<ScheduleDefinition> captor = ArgumentCaptor.forClass(ScheduleDefinition.class);
    verify(manager).save(captor.capture());
    ScheduleDefinition definition = captor.getValue();
    assertThat(definition.key().namespace()).isEqualTo("yak-ops-workflow");
    assertThat(definition.key().name()).isEqualTo("schedule-1");
    assertThat(definition.trigger().expression()).isEqualTo("0 0 2 * * ?");
    assertThat(definition.trigger().zoneId().getId()).isEqualTo("Asia/Shanghai");
    assertThat(definition.target().handler()).isEqualTo("workflowScheduleHandler");
    assertThat(definition.target().payload()).containsEntry("scheduleId", "schedule-1");
    assertThat(definition.target().payload()).containsEntry("workflowId", "workflow-1");
    assertThat(definition.policy().concurrencyPolicy()).isEqualTo(ConcurrencyPolicy.FORBID);
    assertThat(definition.policy().misfirePolicy()).isEqualTo(MisfirePolicy.FIRE_ONCE_NOW);
    assertThat(definition.policy().triggerRetries()).isZero();
    assertThat(definition.enabled()).isTrue();
  }

  @Test
  void shouldMapParallelAndSkipPolicies() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);
    WorkflowScheduleEngineBridge bridge = new WorkflowScheduleEngineBridge(provider);

    ScheduleDefinition definition = bridge.toDefinition(schedule("PARALLEL", "SKIP"));

    assertThat(definition.policy().concurrencyPolicy()).isEqualTo(ConcurrencyPolicy.ALLOW);
    assertThat(definition.policy().misfirePolicy()).isEqualTo(MisfirePolicy.IGNORE);
  }

  private WorkflowSchedulePO schedule(String concurrency, String misfire) {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setName("每日订单同步");
    value.setStatus("ONLINE");
    value.setCronExpression("0 0 2 * * ?");
    value.setTimezone("Asia/Shanghai");
    value.setExecutionStrategy(concurrency);
    value.setMisfireStrategy(misfire);
    return value;
  }
}
