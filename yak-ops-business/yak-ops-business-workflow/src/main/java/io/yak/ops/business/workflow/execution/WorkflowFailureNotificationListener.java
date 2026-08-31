package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.execution.WorkflowExecutionNotificationReader.Snapshot;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Publishes actionable notifications for failed or timed-out Workflow executions. */
@Component
public class WorkflowFailureNotificationListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WorkflowFailureNotificationListener.class);

  private final WorkflowExecutionNotificationReader executionReader;
  private final ObjectProvider<BusinessNotificationGateway> notificationGateways;

  public WorkflowFailureNotificationListener(
      WorkflowExecutionNotificationReader executionReader,
      ObjectProvider<BusinessNotificationGateway> notificationGateways) {
    this.executionReader = executionReader;
    this.notificationGateways = notificationGateways;
  }

  @EventListener
  public void onTerminal(WorkflowExecutionTerminalEvent event) {
    if (event == null || !notifiable(event.executionStatus())) return;
    BusinessNotificationGateway gateway = notificationGateways.getIfAvailable();
    if (gateway == null) return;

    try {
      Snapshot execution = executionReader.find(event.executionId()).orElse(null);
      if (execution == null) return;

      String workflowName = StringUtils.hasText(execution.workflowName())
          ? execution.workflowName().trim()
          : "工作流 " + event.executionId();
      boolean timedOut = "TIMED_OUT".equalsIgnoreCase(event.executionStatus());
      String title = timedOut ? "工作流执行超时" : "工作流执行失败";
      String content = StringUtils.hasText(execution.errorMessage())
          ? execution.errorMessage().trim()
          : timedOut
              ? "工作流执行已超时，请查看实例详情。"
              : "工作流执行失败，请查看实例详情。";

      gateway.publishToProjectOwners(
          new BusinessNotification(
              execution.projectId(),
              BusinessNotification.Type.TASK,
              BusinessNotification.Level.ERROR,
              title,
              workflowName,
              content,
              "WORKFLOW_EXECUTION",
              event.executionId(),
              "/workflow/instances/" + event.executionId()));
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to publish Workflow failure notification: execution={}, status={}",
          event.executionId(),
          event.executionStatus(),
          exception);
    }
  }

  private boolean notifiable(String status) {
    return "FAILED".equalsIgnoreCase(status) || "TIMED_OUT".equalsIgnoreCase(status);
  }
}
