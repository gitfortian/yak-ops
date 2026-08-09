package io.yak.ops.business.workflow.persistence;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Durable business catalog boundary for editable workflows and immutable published versions. */
public interface WorkflowDefinitionPersistence {

  List<DefinitionRecord> loadDefinitions();

  List<VersionRecord> loadVersions(String workflowId);

  void saveDefinition(DefinitionRecord definition);

  /** Persist a new immutable version and advance the current definition projection atomically. */
  void publish(DefinitionRecord definition, VersionRecord version);

  void deleteDefinition(String workflowId);

  record DefinitionRecord(
      String id,
      String name,
      String description,
      String status,
      String failureStrategy,
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds,
      long draftRevision,
      int latestVersionNo,
      String activeVersionId,
      String latestExecutionId,
      String latestExecutionStatus,
      Instant createTime,
      Instant updateTime) {
  }

  record VersionRecord(
      String id,
      String workflowId,
      int versionNo,
      long draftRevision,
      WorkflowRunSpec runSpec,
      Map<String, Object> editorMeta,
      Map<String, TaskVersionSnapshot> taskVersionsByNode,
      Instant publishedAt) {
  }
}
