package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import java.util.List;
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

  private final WorkflowExecutionDao executionDao;
  private final ObjectProvider<BusinessNotificationGateway> notificationGateways;

  public WorkflowFailureNotificationListener(
      WorkflowExecutionDao executionDao,
      ObjectProvider<BusinessNotificationGateway> notificationGateways) {
    this.executionDao = executionDao;
    this.notificationGateways = notificationGateways;
  }

  @EventListener
  public void onTerminal(WorkflowExecutionTerminalEvent event) {
    if (event == null || !notifiable(event.executionStatus())) return;
    BusinessNotificationGateway gateway = notificationGateways.getIfAvailable();
    if (gateway == null) return;

    try {
      WorkflowExecutionPO execution = executionDao.selectExecution(event.executionId());
      if (execution == null || execution.getProjectId() == null || execution.getProjectId() <= 0L) {
        return;
      }

      String workflowName = StringUtils.hasText(execution.getWorkflowName())
          ? execution.getWorkflowName().trim()
          : "工作流 " + execution.getDefinitionId();
      String error = firstError(executionDao.selectNodeExecutions(event.executionId()));
      boolean timedOut = "TIMED_OUT".equalsIgnoreCase(event.executionStatus());
      String title = timedOut ? "工作流执行超时" : "工作流执行失败";
      String content = error == null
          ? (timedOut ? "工作流执行已超时，请查看实例详情。" : "工作流执行失败，请查看实例详情。")
          : error;

      gateway.publishToProjectOwners(
          new BusinessNotification(
              execution.getProjectId(),
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

  private String firstError(List<WorkflowNodeExecutionPO> nodes) {
    if (nodes == null) return null;
    return nodes.stream()
        .map(WorkflowNodeExecutionPO::getErrorMessage)
        .filter(StringUtils::hasText)
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }
}
