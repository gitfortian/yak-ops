package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 工作流定义展示对象。 */
public record WorkflowDefinitionVO(
    String id,
    String name,
    String description,
    String status,
    int nodeCount,
    int edgeCount,
    List<NodeVO> nodes,
    List<EdgeVO> edges,
    Map<String, Object> input,
    Map<String, Object> editorMeta,
    long workflowTimeoutSeconds,
    String failureStrategy,
    String activeVersionId,
    Integer activeVersionNo,
    int latestVersionNo,
    boolean draftChanged,
    String latestExecutionId,
    String latestExecutionStatus,
    Instant createTime,
    Instant updateTime) {

  public record NodeVO(
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
  }

  public record EdgeVO(String source, String target) {
  }
}
