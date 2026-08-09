package io.yak.ops.business.workflow.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工作流运行节点规格，不包含画布坐标等编辑器元数据。 */
public record WorkflowRunNodeSpec(
    String id,
    String taskId,
    int maxAttempts,
    long retryDelaySeconds,
    long dispatchTimeoutSeconds,
    long executionTimeoutSeconds,
    Map<String, String> inputMapping,
    String triggerRule,
    String failurePolicy) {

  public WorkflowRunNodeSpec {
    inputMapping = inputMapping == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(inputMapping));
    triggerRule = triggerRule == null || triggerRule.isBlank() ? "ALL_SUCCESS" : triggerRule;
    failurePolicy = failurePolicy == null || failurePolicy.isBlank()
        ? "FAIL_WORKFLOW"
        : failurePolicy;
  }
}
