package io.yak.ops.business.development.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionPage;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSummary;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/** Durable execution history and persistence boundary for data-development manual runs. */
@Service
public class DevelopmentTaskExecutionService {

  private static final TypeReference<Map<String, Object>> OUTPUT_TYPE = new TypeReference<>() {};
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_OUTPUT_JSON_LENGTH = 2_000_000;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentTaskExecutionService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      CurrentProject currentProject) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.currentProject = currentProject;
  }

  public DevelopmentTaskExecutionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this(jdbcTemplate, objectMapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  /** Source-compatible entry for callers that predate persisted schema/retry metadata. */
  public long createPending(
      DevelopmentNode node,
      String taskType,
      String content,
      String configJson,
      String operatorName) {
    return createPending(node, taskType, 1, content, configJson, operatorName, null);
  }

  public long createPending(
      DevelopmentNode node,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName,
      Long retryOfExecutionId) {
    ensureCurrentProject(node.projectId());
    String sql = "INSERT INTO yak_dev_task_execution "
        + "(project_id, node_id, task_name, task_type, schema_version, trigger_type, status, operator_name, "
        + "retry_of_execution_id, content, config_json, start_time, create_time, update_time) "
        + "VALUES (?, ?, ?, ?, ?, 'MANUAL', 'PENDING', ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))";
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          if (node.projectId() == null) statement.setObject(1, null);
          else statement.setLong(1, node.projectId());
          statement.setLong(2, node.id());
          statement.setString(3, node.name());
          statement.setString(4, normalizeUpper(taskType));
          statement.setInt(5, Math.max(1, schemaVersion));
          statement.setString(6, normalizeOperator(operatorName));
          if (retryOfExecutionId == null) statement.setObject(7, null);
          else statement.setLong(7, retryOfExecutionId);
          statement.setString(8, content == null ? "" : content);
          statement.setString(9, configJson == null || configJson.isBlank() ? "{}" : configJson);
          return statement;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建运行记录失败：未返回主键");
    return key.longValue();
  }

  public void attachRuntime(long id, String runtimeExecutionId, String status) {
    String normalized = normalizeUpper(status);
    if (!"PENDING".equals(normalized) && !"RUNNING".equals(normalized)) normalized = "RUNNING";
    Long projectId = currentProjectId();
    String sql = "UPDATE yak_dev_task_execution SET runtime_execution_id = ?, status = ?, update_time = NOW(6) "
        + "WHERE id = ? AND status IN ('PENDING', 'RUNNING')"
        + (projectId == null ? "" : " AND project_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(runtimeExecutionId);
    args.add(normalized);
    args.add(id);
    if (projectId != null) args.add(projectId);
    jdbcTemplate.update(sql, args.toArray());
  }

  public void markRunning(long id, String runtimeExecutionId) {
    attachRuntime(id, runtimeExecutionId, "RUNNING");
  }

  public void updateActiveStatus(long id, String status) {
    String normalized = normalizeUpper(status);
    if (!"PENDING".equals(normalized) && !"RUNNING".equals(normalized)) return;
    Long projectId = currentProjectId();
    String sql = "UPDATE yak_dev_task_execution SET status = ?, update_time = NOW(6) "
        + "WHERE id = ? AND status IN ('PENDING', 'RUNNING')"
        + (projectId == null ? "" : " AND project_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(normalized);
    args.add(id);
    if (projectId != null) args.add(projectId);
    jdbcTemplate.update(sql, args.toArray());
  }

  public void complete(
      long id,
      String status,
      long durationMs,
      String errorMessage,
      Map<String, Object> output) {
    Long projectId = currentProjectId();
    String sql = "UPDATE yak_dev_task_execution SET status = ?, duration_ms = ?, error_message = ?, output_json = ?, "
        + "end_time = NOW(6), update_time = NOW(6) WHERE id = ?"
        + (projectId == null ? "" : " AND project_id = ?")
        + " AND status IN ('PENDING', 'RUNNING')";
    List<Object> args = new ArrayList<>();
    args.add(normalizeUpper(status));
    args.add(Math.max(0L, durationMs));
    args.add(trim(errorMessage, 1000));
    args.add(serializeOutput(output));
    args.add(id);
    if (projectId != null) args.add(projectId);
    jdbcTemplate.update(sql, args.toArray());
  }

  public DevelopmentTaskExecutionPage page(
      int pageNo,
      int pageSize,
      String keyword,
      String status,
      String taskType,
      String triggerType,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    int normalizedPageNo = Math.max(1, pageNo);
    int normalizedPageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    List<Object> args = new ArrayList<>();
    String where = buildWhere(keyword, status, taskType, triggerType, startTime, endTime, args);

    Long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM yak_dev_task_execution" + where,
        Long.class,
        args.toArray());

    List<Object> rowArgs = new ArrayList<>(args);
    rowArgs.add(normalizedPageSize);
    rowArgs.add((normalizedPageNo - 1) * normalizedPageSize);
    List<DevelopmentTaskExecutionSummary> records = jdbcTemplate.query(
        "SELECT id, node_id, task_name, task_type, schema_version, trigger_type, runtime_execution_id, "
            + "retry_of_execution_id, status, operator_name, duration_ms, error_message, start_time, end_time "
            + "FROM yak_dev_task_execution"
            + where
            + " ORDER BY start_time DESC, id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) -> new DevelopmentTaskExecutionSummary(
            rs.getLong("id"),
            rs.getLong("node_id"),
            rs.getString("task_name"),
            rs.getString("task_type"),
            rs.getInt("schema_version"),
            rs.getString("trigger_type"),
            rs.getString("runtime_execution_id"),
            nullableLong(rs.getLong("retry_of_execution_id"), rs.wasNull()),
            rs.getString("status"),
            rs.getString("operator_name"),
            nullableLong(rs.getLong("duration_ms"), rs.wasNull()),
            rs.getString("error_message"),
            toLocalDateTime(rs.getTimestamp("start_time")),
            toLocalDateTime(rs.getTimestamp("end_time"))),
        rowArgs.toArray());

    return new DevelopmentTaskExecutionPage(
        records,
        total == null ? 0L : total,
        normalizedPageNo,
        normalizedPageSize);
  }

  public DevelopmentTaskExecutionDetail get(long id) {
    Long projectId = currentProjectId();
    String sql = detailSelect()
        + " WHERE id = ?"
        + (projectId == null ? "" : " AND project_id = ?")
        + " LIMIT 1";
    Object[] args = projectId == null ? new Object[] {id} : new Object[] {id, projectId};
    List<DevelopmentTaskExecutionDetail> records = jdbcTemplate.query(sql, this::mapDetail, args);
    if (records.isEmpty()) throw new IllegalArgumentException("运行记录不存在：" + id);
    return records.get(0);
  }

  Optional<DevelopmentTaskExecutionDetail> findLatestActiveByNode(long nodeId) {
    Long projectId = currentProjectId();
    String sql = detailSelect()
        + " WHERE node_id = ? AND status IN ('PENDING', 'RUNNING')"
        + (projectId == null ? "" : " AND project_id = ?")
        + " ORDER BY start_time DESC, id DESC LIMIT 1";
    Object[] args = projectId == null
        ? new Object[] {nodeId}
        : new Object[] {nodeId, projectId};
    List<DevelopmentTaskExecutionDetail> records = jdbcTemplate.query(sql, this::mapDetail, args);
    return records.stream().findFirst();
  }

  List<DevelopmentTaskExecutionDetail> listActiveForReconciliation(int limit) {
    int safeLimit = Math.max(1, Math.min(500, limit));
    return jdbcTemplate.query(
        detailSelect()
            + " WHERE status IN ('PENDING', 'RUNNING')"
            + " ORDER BY update_time ASC, id ASC LIMIT ?",
        this::mapDetail,
        safeLimit);
  }

  private DevelopmentTaskExecutionDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
    return new DevelopmentTaskExecutionDetail(
        rs.getLong("id"),
        rs.getLong("node_id"),
        rs.getString("task_name"),
        rs.getString("task_type"),
        rs.getInt("schema_version"),
        rs.getString("trigger_type"),
        rs.getString("runtime_execution_id"),
        nullableLong(rs.getLong("retry_of_execution_id"), rs.wasNull()),
        rs.getString("status"),
        rs.getString("operator_name"),
        nullableLong(rs.getLong("duration_ms"), rs.wasNull()),
        rs.getString("error_message"),
        rs.getString("content"),
        rs.getString("config_json"),
        parseOutput(rs.getString("output_json")),
        toLocalDateTime(rs.getTimestamp("start_time")),
        toLocalDateTime(rs.getTimestamp("end_time")));
  }

  private String detailSelect() {
    return "SELECT id, node_id, task_name, task_type, schema_version, trigger_type, runtime_execution_id, "
        + "retry_of_execution_id, status, operator_name, duration_ms, error_message, content, config_json, "
        + "output_json, start_time, end_time FROM yak_dev_task_execution";
  }

  private String buildWhere(
      String keyword,
      String status,
      String taskType,
      String triggerType,
      LocalDateTime startTime,
      LocalDateTime endTime,
      List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    Long projectId = currentProjectId();
    if (projectId != null) {
      where.append(" AND project_id = ?");
      args.add(projectId);
    }
    String normalizedKeyword = trim(keyword, 200);
    if (normalizedKeyword != null && !normalizedKeyword.isBlank()) {
      where.append(" AND (task_name LIKE ? OR runtime_execution_id LIKE ? OR operator_name LIKE ?)");
      String like = "%" + normalizedKeyword.trim() + "%";
      args.add(like);
      args.add(like);
      args.add(like);
    }
    appendEquals(where, args, "status", status);
    appendEquals(where, args, "task_type", taskType);
    appendEquals(where, args, "trigger_type", triggerType);
    if (startTime != null) {
      where.append(" AND start_time >= ?");
      args.add(Timestamp.valueOf(startTime));
    }
    if (endTime != null) {
      where.append(" AND start_time <= ?");
      args.add(Timestamp.valueOf(endTime));
    }
    return where.toString();
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private void ensureCurrentProject(Long ownerProjectId) {
    currentProject.current().ifPresent(
        context -> {
          if (!Objects.equals(context.projectId(), ownerProjectId)) {
            throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
          }
        });
  }

  private void appendEquals(StringBuilder where, List<Object> args, String column, String value) {
    if (value == null || value.isBlank()) return;
    where.append(" AND ").append(column).append(" = ?");
    args.add(normalizeUpper(value));
  }

  private String serializeOutput(Map<String, Object> output) {
    try {
      String json = objectMapper.writeValueAsString(output == null ? Map.of() : output);
      if (json.length() <= MAX_OUTPUT_JSON_LENGTH) return json;
      return objectMapper.writeValueAsString(Map.of(
          "truncated", true,
          "message", "运行结果过大，历史记录仅保留概要"));
    } catch (Exception exception) {
      return "{}";
    }
  }

  private Map<String, Object> parseOutput(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, OUTPUT_TYPE);
    } catch (Exception exception) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("message", "历史运行结果无法解析");
      return fallback;
    }
  }

  private String normalizeUpper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOperator(String value) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isBlank() ? "unknown" : trim(normalized, 128);
  }

  private String trim(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() > max ? normalized.substring(0, max) : normalized;
  }

  private Long nullableLong(long value, boolean wasNull) {
    return wasNull ? null : value;
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
