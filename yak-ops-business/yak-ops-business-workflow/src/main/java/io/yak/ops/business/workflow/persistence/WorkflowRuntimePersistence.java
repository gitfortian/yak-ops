package io.yak.ops.business.workflow.persistence;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable runtime metadata and external-attempt binding needed for restart recovery. */
public interface WorkflowRuntimePersistence {

  void saveMetadata(String executionId, RuntimeMetadataRecord metadata);

  Optional<RuntimeMetadataRecord> findMetadata(String executionId);

  List<String> listExecutionIds();

  List<String> findRecoverableExecutionIds();

  void bindExternalExecution(String attemptId, String externalExecutionId);

  Optional<String> findExternalExecution(String attemptId);

  record RuntimeMetadataRecord(
      String name,
      int edgeCount,
      long workflowTimeoutSeconds,
      String failureStrategy,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun,
      Map<String, NodeMetadataRecord> nodes) {

    public RuntimeMetadataRecord {
      nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }
  }

  record NodeMetadataRecord(
      TaskVersionSnapshot task,
      String triggerRule,
      String failurePolicy,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping) {

    public NodeMetadataRecord {
      inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
    }
  }
}
