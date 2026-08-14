package io.yak.ops.business.workflow.service;

import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 同一个 WorkflowExecution 的人工恢复入口。
 *
 * <p>retry / continue 与 restart 不同：它们不会创建新的 WorkflowExecution，而是可能把一个已经终态的
 * Execution 重新拉回 RUNNING。数据库模式下必须先经过 Trigger Ledger 协调，避免绕过 SERIAL_WAIT /
 * SERIAL_DISCARD 的工作流级并发语义；database-disabled 的 focused/dev 模式保持 Runtime 直连。</p>
 */
@Service
public class WorkflowExecutionReactivationService {
  private final WorkflowRuntimeService runtime;
  private final ObjectProvider<WorkflowScheduleTriggerCoordinator> coordinator;

  public WorkflowExecutionReactivationService(
      WorkflowRuntimeService runtime,
      ObjectProvider<WorkflowScheduleTriggerCoordinator> coordinator) {
    this.runtime = runtime;
    this.coordinator = coordinator;
  }

  public WorkflowInstanceVO continueAfterFailure(String executionId, String nodeId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    String node = required(nodeId, "工作流节点 ID 不能为空");
    return reactivate(
        id,
        "CONTINUE_AFTER_FAILURE",
        () -> runtime.continueAfterFailure(id, node));
  }

  public WorkflowInstanceVO retryFailedNode(String executionId, String nodeId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    String node = required(nodeId, "工作流节点 ID 不能为空");
    return reactivate(
        id,
        "RETRY_FAILED_NODE",
        () -> runtime.retryFailedNode(id, node));
  }

  public WorkflowInstanceVO retryFailedNodes(String executionId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    return reactivate(
        id,
        "RETRY_FAILED_NODES",
        () -> runtime.retryFailedNodes(id));
  }

  private WorkflowInstanceVO reactivate(
      String executionId,
      String operation,
      Supplier<WorkflowInstanceVO> action) {
    WorkflowScheduleTriggerCoordinator value = coordinator.getIfAvailable();
    if (value == null) return action.get();
    return value.reactivateExecution(executionId, operation, action);
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
