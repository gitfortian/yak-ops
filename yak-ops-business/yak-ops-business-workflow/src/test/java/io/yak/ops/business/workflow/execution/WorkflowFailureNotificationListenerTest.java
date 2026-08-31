package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.execution.WorkflowExecutionNotificationReader.Snapshot;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class WorkflowFailureNotificationListenerTest {

  @Test
  @SuppressWarnings("unchecked")
  void failedExecutionPublishesProjectOwnedTaskNotification() {
    WorkflowExecutionNotificationReader executions = mock(WorkflowExecutionNotificationReader.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);
    when(executions.find("exec-1")).thenReturn(Optional.of(
        new Snapshot(7L, "exec-1", "每日订单加工", "FAILED", "SQL task failed")));

    WorkflowFailureNotificationListener listener =
        new WorkflowFailureNotificationListener(executions, provider);
    listener.onTerminal(new WorkflowExecutionTerminalEvent("exec-1", "FAILED", Instant.now()));

    ArgumentCaptor<BusinessNotification> captor =
        ArgumentCaptor.forClass(BusinessNotification.class);
    verify(gateway).publishToProjectOwners(captor.capture());
    BusinessNotification notification = captor.getValue();
    assertThat(notification.projectId()).isEqualTo(7L);
    assertThat(notification.type()).isEqualTo(BusinessNotification.Type.TASK);
    assertThat(notification.level()).isEqualTo(BusinessNotification.Level.ERROR);
    assertThat(notification.title()).isEqualTo("工作流执行失败");
    assertThat(notification.summary()).isEqualTo("每日订单加工");
    assertThat(notification.content()).isEqualTo("SQL task failed");
    assertThat(notification.actionPath()).isEqualTo("/workflow/instances/exec-1");
  }

  @Test
  @SuppressWarnings("unchecked")
  void successfulExecutionDoesNotPublishNotification() {
    WorkflowExecutionNotificationReader executions = mock(WorkflowExecutionNotificationReader.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);

    WorkflowFailureNotificationListener listener =
        new WorkflowFailureNotificationListener(executions, provider);
    listener.onTerminal(new WorkflowExecutionTerminalEvent("exec-1", "SUCCESS", Instant.now()));

    verify(executions, never()).find(any());
    verify(gateway, never()).publishToProjectOwners(any());
  }
}
