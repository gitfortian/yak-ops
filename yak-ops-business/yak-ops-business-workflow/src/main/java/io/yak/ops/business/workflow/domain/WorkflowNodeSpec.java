package io.yak.ops.business.workflow.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工作流节点领域规格。 */
public record WorkflowNodeSpec(
    String id,
    String taskId,
    double positionX,
    double positionY,
    int maxAttempts,
    long retryDelaySeconds,
    long dispatchTimeoutSeconds,
    long executionTimeoutSeconds,
    Map<String, String> inputMapping,
    String triggerRule,
    String failurePolicy) {

  public WorkflowNodeSpec {
    inputMapping = inputMapping == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(inputMapping));
    triggerRule = triggerRule == null || triggerRule.isBlank() ? "ALL_SUCCESS" : triggerRule;
    failurePolicy = failurePolicy == null || failurePolicy.isBlank()
        ? "FAIL_WORKFLOW"
        : failurePolicy;
  }
}
