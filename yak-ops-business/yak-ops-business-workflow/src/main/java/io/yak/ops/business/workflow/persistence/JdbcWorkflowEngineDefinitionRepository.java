package io.yak.ops.business.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.definition.NodeInputMapping;
import io.yak.framework.workflow.engine.definition.NodeTimeoutPolicy;
import io.yak.framework.workflow.engine.definition.RetryPolicy;
import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowFailureStrategy;
import io.yak.framework.workflow.engine.definition.WorkflowTimeoutPolicy;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Database-backed Yak Framework definition repository using immutable workflow-version rows. */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcWorkflowEngineDefinitionRepository implements WorkflowDefinitionRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowEngineDefinitionRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(WorkflowDefinition definition) {
    String json = write(EngineDefinitionSnapshot.from(definition));
    StoredDefinition stored = stored(definition.id());
    if (!stored.exists()) {
      jdbc.update(
          "INSERT INTO yak_workflow_version "
              + "(id,workflow_id,version_no,version_kind,draft_revision,engine_definition_json,create_time) "
              + "VALUES (?,NULL,NULL,'RUNTIME',NULL,?,?)",
          definition.id(),
          json,
          Timestamp.from(Instant.now()));
      return;
    }

    if (stored.json() == null || stored.json().isBlank()) {
      int updated = jdbc.update(
          "UPDATE yak_workflow_version SET engine_definition_json=? "
              + "WHERE id=? AND engine_definition_json IS NULL",
          json,
          definition.id());
      if (updated > 0) {
        return;
      }
      stored = stored(definition.id());
    }

    if (!json.equals(stored.json())) {
      throw new IllegalStateException(
          "工作流版本的 Engine Definition 已固定，禁止覆盖：" + definition.id());
    }
  }

  @Override
  public Optional<WorkflowDefinition> findById(String definitionId) {
    try {
      String json = jdbc.queryForObject(
          "SELECT engine_definition_json FROM yak_workflow_version WHERE id=?",
          String.class,
          definitionId);
      if (json == null || json.isBlank()) return Optional.empty();
      return Optional.of(read(json).toDefinition());
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  private StoredDefinition stored(String definitionId) {
    List<String> values = jdbc.query(
        "SELECT engine_definition_json FROM yak_workflow_version WHERE id=?",
        (rs, row) -> rs.getString(1),
        definitionId);
    return values.isEmpty()
        ? new StoredDefinition(false, null)
        : new StoredDefinition(true, values.get(0));
  }

  private String write(EngineDefinitionSnapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化 Engine Definition 失败", exception);
    }
  }

  private EngineDefinitionSnapshot read(String json) {
    try {
      return objectMapper.readValue(json, EngineDefinitionSnapshot.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取 Engine Definition 失败", exception);
    }
  }

  private record StoredDefinition(boolean exists, String json) {
  }

  private record EngineDefinitionSnapshot(
      String id,
      String name,
      String failureStrategy,
      long workflowTimeoutMillis,
      List<NodeSnapshot> nodes,
      List<EdgeSnapshot> edges) {

    static EngineDefinitionSnapshot from(WorkflowDefinition definition) {
      return new EngineDefinitionSnapshot(
          definition.id(),
          definition.name(),
          definition.failureStrategy().name(),
          definition.timeoutPolicy().timeout().toMillis(),
          definition.nodes().values().stream().map(NodeSnapshot::from).toList(),
          definition.edges().stream().map(EdgeSnapshot::from).toList());
    }

    WorkflowDefinition toDefinition() {
      return new WorkflowDefinition(
          id,
          name,
          WorkflowFailureStrategy.valueOf(failureStrategy),
          workflowTimeoutMillis > 0L
              ? WorkflowTimeoutPolicy.of(Duration.ofMillis(workflowTimeoutMillis))
              : WorkflowTimeoutPolicy.none(),
          nodes.stream().map(NodeSnapshot::toDefinition).toList(),
          edges.stream().map(EdgeSnapshot::toDefinition).toList());
    }
  }

  private record NodeSnapshot(
      String id,
      String name,
      String triggerRule,
      int maxAttempts,
      long retryDelayMillis,
      String failurePolicy,
      long dispatchTimeoutMillis,
      long executionTimeoutMillis,
      Map<String, String> inputMapping,
      Map<String, Object> configuration) {

    static NodeSnapshot from(NodeDefinition node) {
      return new NodeSnapshot(
          node.id(),
          node.name(),
          node.triggerRule().name(),
          node.retryPolicy().maxAttempts(),
          node.retryPolicy().delay().toMillis(),
          node.failurePolicy().name(),
          node.timeoutPolicy().dispatchTimeout().toMillis(),
          node.timeoutPolicy().executionTimeout().toMillis(),
          node.inputMapping().bindings(),
          node.configuration());
    }

    NodeDefinition toDefinition() {
      return new NodeDefinition(
          id,
          name,
          TriggerRule.valueOf(triggerRule),
          maxAttempts > 1
              ? RetryPolicy.fixed(maxAttempts, Duration.ofMillis(retryDelayMillis))
              : RetryPolicy.none(),
          NodeFailurePolicy.valueOf(failurePolicy),
          NodeTimeoutPolicy.of(
              Duration.ofMillis(dispatchTimeoutMillis),
              Duration.ofMillis(executionTimeoutMillis)),
          NodeInputMapping.of(inputMapping),
          configuration);
    }
  }

  private record EdgeSnapshot(String fromNodeId, String toNodeId) {
    static EdgeSnapshot from(EdgeDefinition edge) {
      return new EdgeSnapshot(edge.fromNodeId(), edge.toNodeId());
    }

    EdgeDefinition toDefinition() {
      return new EdgeDefinition(fromNodeId, toNodeId);
    }
  }
}
