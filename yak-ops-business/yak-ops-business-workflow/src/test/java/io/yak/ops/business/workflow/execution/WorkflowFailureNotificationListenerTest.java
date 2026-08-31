package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class WorkflowFailureNotificationListenerTest {

  @Test
  @SuppressWarnings("unchecked")
  void failedExecutionPublishesProjectOwnedTaskNotification() {
    WorkflowExecutionDao executions = mock(WorkflowExecutionDao.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);

    WorkflowExecutionPO execution = new WorkflowExecutionPO();
    execution.setId("exec-1");
    execution.setProjectId(7L);
    execution.setDefinitionId("workflow-1");
    execution.setWorkflowName("每日订单加工");
    when(executions.selectExecution("exec-1")).thenReturn(execution);

    WorkflowNodeExecutionPO node = new WorkflowNodeExecutionPO();
    node.setErrorMessage("SQL task failed");
    when(executions.selectNodeExecutions("exec-1")).thenReturn(List.of(node));

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
    WorkflowExecutionDao executions = mock(WorkflowExecutionDao.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);

    WorkflowFailureNotificationListener listener =
        new WorkflowFailureNotificationListener(executions, provider);
    listener.onTerminal(new WorkflowExecutionTerminalEvent("exec-1", "SUCCESS", Instant.now()));

    verify(executions, never()).selectExecution(any());
    verify(gateway, never()).publishToProjectOwners(any());
  }
}
