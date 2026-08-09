package io.yak.ops.business.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.EdgeRequest;
import io.yak.ops.business.workflow.model.WorkflowDefinitionUpdateRequest.NodeRequest;
import io.yak.ops.business.workflow.model.WorkflowRunRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC catalog for editable workflow definitions and immutable published versions. */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcWorkflowCatalogRepository implements WorkflowDefinitionPersistence {

  private static final TypeReference<Map<String, TaskVersionSnapshot>> TASK_VERSIONS =
      new TypeReference<>() { };

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowCatalogRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<DefinitionRecord> loadDefinitions() {
    return jdbc.query(
        "SELECT * FROM yak_workflow_definition ORDER BY update_time DESC",
        this::mapDefinition);
  }

  @Override
  public List<VersionRecord> loadVersions(String workflowId) {
    return jdbc.query(
        "SELECT * FROM yak_workflow_version "
            + "WHERE workflow_id=? AND version_kind='PUBLISHED' ORDER BY version_no",
        this::mapVersion,
        workflowId);
  }

  @Override
  public void saveDefinition(DefinitionRecord definition) {
    DraftPayload payload = new DraftPayload(
        definition.failureStrategy(),
        definition.nodes(),
        definition.edges(),
        definition.input(),
        definition.editorMeta(),
        definition.workflowTimeoutSeconds());
    jdbc.update(
        "INSERT INTO yak_workflow_definition "
            + "(id,name,description,status,draft_revision,latest_version_no,active_version_id,"
            + "draft_json,latest_execution_id,latest_execution_status,create_time,update_time) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),"
            + "status=VALUES(status),draft_revision=VALUES(draft_revision),"
            + "latest_version_no=VALUES(latest_version_no),active_version_id=VALUES(active_version_id),"
            + "draft_json=VALUES(draft_json),latest_execution_id=VALUES(latest_execution_id),"
            + "latest_execution_status=VALUES(latest_execution_status),update_time=VALUES(update_time)",
        definition.id(),
        definition.name(),
        definition.description(),
        definition.status(),
        definition.draftRevision(),
        definition.latestVersionNo(),
        definition.activeVersionId(),
        write(payload),
        definition.latestExecutionId(),
        definition.latestExecutionStatus(),
        timestamp(definition.createTime()),
        timestamp(definition.updateTime()));
  }

  @Override
  public void saveVersion(VersionRecord version) {
    jdbc.update(
        "INSERT INTO yak_workflow_version "
            + "(id,workflow_id,version_no,version_kind,draft_revision,run_request_json,"
            + "editor_meta_json,task_versions_json,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
        version.id(),
        version.workflowId(),
        version.versionNo(),
        "PUBLISHED",
        version.draftRevision(),
        write(version.runRequest()),
        write(version.editorMeta()),
        write(version.taskVersionsByNode()),
        timestamp(version.publishedAt()));
  }

  @Override
  public void deleteDefinition(String workflowId) {
    // Published versions are intentionally retained because historical executions still reference them.
    jdbc.update("DELETE FROM yak_workflow_definition WHERE id=?", workflowId);
  }

  private DefinitionRecord mapDefinition(ResultSet rs, int row) throws SQLException {
    DraftPayload draft = read(rs.getString("draft_json"), DraftPayload.class);
    return new DefinitionRecord(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("status"),
        draft.failureStrategy(),
        draft.nodes(),
        draft.edges(),
        draft.input(),
        draft.editorMeta(),
        draft.workflowTimeoutSeconds(),
        rs.getLong("draft_revision"),
        rs.getInt("latest_version_no"),
        rs.getString("active_version_id"),
        rs.getString("latest_execution_id"),
        rs.getString("latest_execution_status"),
        instant(rs.getTimestamp("create_time")),
        instant(rs.getTimestamp("update_time")));
  }

  private VersionRecord mapVersion(ResultSet rs, int row) throws SQLException {
    return new VersionRecord(
        rs.getString("id"),
        rs.getString("workflow_id"),
        rs.getInt("version_no"),
        rs.getLong("draft_revision"),
        read(rs.getString("run_request_json"), WorkflowRunRequest.class),
        readMap(rs.getString("editor_meta_json")),
        readTaskVersions(rs.getString("task_versions_json")),
        instant(rs.getTimestamp("create_time")));
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化工作流持久化快照失败", exception);
    }
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流持久化快照失败", exception);
    }
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流 JSON Map 失败", exception);
    }
  }

  private Map<String, TaskVersionSnapshot> readTaskVersions(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, TASK_VERSIONS);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流任务版本快照失败", exception);
    }
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private record DraftPayload(
      String failureStrategy,
      List<NodeRequest> nodes,
      List<EdgeRequest> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds) {

    private DraftPayload {
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      edges = edges == null ? List.of() : List.copyOf(edges);
      input = input == null ? Map.of() : Map.copyOf(input);
      editorMeta = editorMeta == null ? Map.of() : Map.copyOf(editorMeta);
      failureStrategy = failureStrategy == null || failureStrategy.isBlank()
          ? "CONTINUE_INDEPENDENT_BRANCHES"
          : failureStrategy;
    }
  }
}
