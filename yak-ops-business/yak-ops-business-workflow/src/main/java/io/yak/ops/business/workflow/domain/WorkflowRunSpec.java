package io.yak.ops.business.workflow.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作流一次运行的不可变领域规格。 */
public record WorkflowRunSpec(
    String name,
    List<WorkflowNodeSpec> nodes,
    List<WorkflowEdgeSpec> edges,
    Map<String, Object> input,
    long workflowTimeoutSeconds,
    String failureStrategy) {

  public WorkflowRunSpec {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
    failureStrategy = failureStrategy == null || failureStrategy.isBlank()
        ? "CONTINUE_INDEPENDENT_BRANCHES"
        : failureStrategy;
  }
}
