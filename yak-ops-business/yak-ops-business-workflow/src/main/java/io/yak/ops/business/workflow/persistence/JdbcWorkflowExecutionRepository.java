package io.yak.ops.business.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.execution.NodeAttemptSnapshot;
import io.yak.framework.workflow.engine.execution.NodeExecutionSnapshot;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecutionSnapshot;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.state.NodeAttemptFailureReason;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Database-backed Yak Framework execution repository. */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcWorkflowExecutionRepository implements ExecutionRepository {

  private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() { };

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transaction;

  public JdbcWorkflowExecutionRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  public void save(WorkflowExecution execution) {
    WorkflowExecutionSnapshot snapshot = execution.snapshot();
    transaction.executeWithoutResult(status -> {
      upsertExecution(snapshot);
      for (NodeExecutionSnapshot node : snapshot.nodes()) {
        upsertNode(node);
        for (NodeAttemptSnapshot attempt : node.attempts()) {
          upsertAttempt(snapshot.id(), node, attempt);
        }
      }
    });
  }

  @Override
  public Optional<WorkflowExecution> findById(String executionId) {
    List<ExecutionRow> executions = jdbc.query(
        "SELECT * FROM yak_workflow_execution WHERE id=?",
        this::mapExecution,
        executionId);
    if (executions.isEmpty()) return Optional.empty();
    ExecutionRow root = executions.get(0);

    Map<String, List<NodeAttemptSnapshot>> attemptsByNodeExecution = new LinkedHashMap<>();
    jdbc.query(
        "SELECT * FROM yak_workflow_node_attempt WHERE workflow_execution_id=? "
            + "ORDER BY node_execution_id,attempt_no",
        (rs, row) -> {
          attemptsByNodeExecution
              .computeIfAbsent(rs.getString("node_execution_id"), ignored -> new ArrayList<>())
              .add(mapAttempt(rs));
          return null;
        },
        executionId);

    List<NodeExecutionSnapshot> nodes = jdbc.query(
        "SELECT * FROM yak_workflow_node_execution WHERE workflow_execution_id=? ORDER BY id",
        (rs, row) -> mapNode(
            rs,
            attemptsByNodeExecution.getOrDefault(rs.getString("id"), List.of())),
        executionId);

    WorkflowExecutionSnapshot snapshot = new WorkflowExecutionSnapshot(
        root.id(),
        root.definitionId(),
        root.sourceExecutionId(),
        root.input(),
        nodes,
        root.createdAt(),
        root.status(),
        root.schedulingStopped(),
        root.runStartedAt(),
        root.pausedAt(),
        root.pausedDuration(),
        root.updatedAt(),
        root.endedAt());
    return Optional.of(WorkflowExecution.restore(snapshot));
  }

  private void upsertExecution(WorkflowExecutionSnapshot value) {
    jdbc.update(
        "INSERT INTO yak_workflow_execution "
            + "(id,definition_id,source_execution_id,status,input_json,scheduling_stopped,"
            + "run_started_at,paused_at,paused_duration_ms,created_at,updated_at,ended_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE definition_id=VALUES(definition_id),"
            + "source_execution_id=VALUES(source_execution_id),status=VALUES(status),"
            + "input_json=VALUES(input_json),scheduling_stopped=VALUES(scheduling_stopped),"
            + "run_started_at=VALUES(run_started_at),paused_at=VALUES(paused_at),"
            + "paused_duration_ms=VALUES(paused_duration_ms),updated_at=VALUES(updated_at),"
            + "ended_at=VALUES(ended_at)",
        value.id(),
        value.definitionId(),
        value.sourceExecutionId(),
        value.status().name(),
        writeMap(value.input()),
        value.schedulingStopped(),
        timestamp(value.runStartedAt()),
        timestamp(value.pausedAt()),
        value.pausedDuration().toMillis(),
        timestamp(value.createdAt()),
        timestamp(value.updatedAt()),
        timestamp(value.endedAt()));
  }

  private void upsertNode(NodeExecutionSnapshot value) {
    jdbc.update(
        "INSERT INTO yak_workflow_node_execution "
            + "(id,workflow_execution_id,node_id,failure_policy,status,output_json,error_message,"
            + "failure_handled,downstream_continuation_allowed) VALUES (?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE failure_policy=VALUES(failure_policy),status=VALUES(status),"
            + "output_json=VALUES(output_json),error_message=VALUES(error_message),"
            + "failure_handled=VALUES(failure_handled),"
            + "downstream_continuation_allowed=VALUES(downstream_continuation_allowed)",
        value.id(),
        value.workflowExecutionId(),
        value.nodeId(),
        value.failurePolicy().name(),
        value.status().name(),
        writeMap(value.output()),
        value.errorMessage(),
        value.failureHandled(),
        value.downstreamContinuationAllowed());
  }

  private void upsertAttempt(
      String workflowExecutionId,
      NodeExecutionSnapshot node,
      NodeAttemptSnapshot value) {
    jdbc.update(
        "INSERT INTO yak_workflow_node_attempt "
            + "(id,node_execution_id,workflow_execution_id,node_id,attempt_no,available_at,status,"
            + "resume_target_status,started_at,paused_at,paused_duration_ms,ended_at,error_message,"
            + "failure_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE available_at=VALUES(available_at),status=VALUES(status),"
            + "resume_target_status=VALUES(resume_target_status),started_at=VALUES(started_at),"
            + "paused_at=VALUES(paused_at),paused_duration_ms=VALUES(paused_duration_ms),"
            + "ended_at=VALUES(ended_at),error_message=VALUES(error_message),"
            + "failure_reason=VALUES(failure_reason)",
        value.id(),
        node.id(),
        workflowExecutionId,
        node.nodeId(),
        value.attemptNumber(),
        timestamp(value.availableAt()),
        value.status().name(),
        value.resumeTargetStatus() == null ? null : value.resumeTargetStatus().name(),
        timestamp(value.startedAt()),
        timestamp(value.pausedAt()),
        value.pausedDuration().toMillis(),
        timestamp(value.endedAt()),
        value.errorMessage(),
        value.failureReason() == null ? null : value.failureReason().name());
  }

  private ExecutionRow mapExecution(ResultSet rs, int row) throws SQLException {
    return new ExecutionRow(
        rs.getString("id"),
        rs.getString("definition_id"),
        rs.getString("source_execution_id"),
        WorkflowExecutionStatus.valueOf(rs.getString("status")),
        readMap(rs.getString("input_json")),
        rs.getBoolean("scheduling_stopped"),
        instant(rs.getTimestamp("run_started_at")),
        instant(rs.getTimestamp("paused_at")),
        Duration.ofMillis(rs.getLong("paused_duration_ms")),
        instant(rs.getTimestamp("created_at")),
        instant(rs.getTimestamp("updated_at")),
        instant(rs.getTimestamp("ended_at")));
  }

  private NodeExecutionSnapshot mapNode(
      ResultSet rs,
      List<NodeAttemptSnapshot> attempts) throws SQLException {
    return new NodeExecutionSnapshot(
        rs.getString("id"),
        rs.getString("workflow_execution_id"),
        rs.getString("node_id"),
        NodeFailurePolicy.valueOf(rs.getString("failure_policy")),
        NodeExecutionStatus.valueOf(rs.getString("status")),
        attempts,
        readMap(rs.getString("output_json")),
        rs.getString("error_message"),
        rs.getBoolean("failure_handled"),
        rs.getBoolean("downstream_continuation_allowed"));
  }

  private NodeAttemptSnapshot mapAttempt(ResultSet rs) throws SQLException {
    String resume = rs.getString("resume_target_status");
    String reason = rs.getString("failure_reason");
    return new NodeAttemptSnapshot(
        rs.getString("id"),
        rs.getInt("attempt_no"),
        instant(rs.getTimestamp("available_at")),
        NodeAttemptStatus.valueOf(rs.getString("status")),
        resume == null ? null : NodeAttemptStatus.valueOf(resume),
        instant(rs.getTimestamp("started_at")),
        instant(rs.getTimestamp("paused_at")),
        Duration.ofMillis(rs.getLong("paused_duration_ms")),
        instant(rs.getTimestamp("ended_at")),
        rs.getString("error_message"),
        reason == null ? null : NodeAttemptFailureReason.valueOf(reason));
  }

  private String writeMap(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化工作流执行 JSON 失败", exception);
    }
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, JSON_MAP);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流执行 JSON 失败", exception);
    }
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private record ExecutionRow(
      String id,
      String definitionId,
      String sourceExecutionId,
      WorkflowExecutionStatus status,
      Map<String, Object> input,
      boolean schedulingStopped,
      Instant runStartedAt,
      Instant pausedAt,
      Duration pausedDuration,
      Instant createdAt,
      Instant updatedAt,
      Instant endedAt) {
  }
}
