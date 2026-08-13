package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.domain.DevelopmentTaskExecutionPage;
import io.yak.ops.business.development.domain.DevelopmentTaskExecutionSummary;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/** Durable run history for data-development tasks. */
@Service
public class DevelopmentTaskExecutionService {

  private static final TypeReference<Map<String, Object>> OUTPUT_TYPE = new TypeReference<>() {};
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_OUTPUT_JSON_LENGTH = 2_000_000;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DevelopmentTaskExecutionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long createPending(
      DevelopmentNode node,
      String taskType,
      String content,
      String configJson,
      String operatorName) {
    String sql = "INSERT INTO yak_dev_task_execution "
        + "(node_id, task_name, task_type, trigger_type, status, operator_name, content, config_json, "
        + "start_time, create_time, update_time) VALUES (?, ?, ?, 'MANUAL', 'PENDING', ?, ?, ?, NOW(6), NOW(6), NOW(6))";
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, node.id());
          statement.setString(2, node.name());
          statement.setString(3, normalizeUpper(taskType));
          statement.setString(4, normalizeOperator(operatorName));
          statement.setString(5, content == null ? "" : content);
          statement.setString(6, configJson == null || configJson.isBlank() ? "{}" : configJson);
          return statement;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建运行记录失败：未返回主键");
    return key.longValue();
  }

  public void markRunning(long id, String runtimeExecutionId) {
    jdbcTemplate.update(
        "UPDATE yak_dev_task_execution SET runtime_execution_id = ?, status = 'RUNNING', update_time = NOW(6) WHERE id = ?",
        runtimeExecutionId,
        id);
  }

  public void complete(
      long id,
      String status,
      long durationMs,
      String errorMessage,
      Map<String, Object> output) {
    jdbcTemplate.update(
        "UPDATE yak_dev_task_execution SET status = ?, duration_ms = ?, error_message = ?, output_json = ?, "
            + "end_time = NOW(6), update_time = NOW(6) WHERE id = ?",
        normalizeUpper(status),
        Math.max(0L, durationMs),
        trim(errorMessage, 1000),
        serializeOutput(output),
        id);
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
        "SELECT id, node_id, task_name, task_type, trigger_type, runtime_execution_id, status, operator_name, "
            + "duration_ms, error_message, start_time, end_time FROM yak_dev_task_execution"
            + where
            + " ORDER BY start_time DESC, id DESC LIMIT ? OFFSET ?",
        (rs, rowNum) -> new DevelopmentTaskExecutionSummary(
            rs.getLong("id"),
            rs.getLong("node_id"),
            rs.getString("task_name"),
            rs.getString("task_type"),
            rs.getString("trigger_type"),
            rs.getString("runtime_execution_id"),
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
    List<DevelopmentTaskExecutionDetail> records = jdbcTemplate.query(
        "SELECT id, node_id, task_name, task_type, trigger_type, runtime_execution_id, status, operator_name, "
            + "duration_ms, error_message, content, config_json, output_json, start_time, end_time "
            + "FROM yak_dev_task_execution WHERE id = ? LIMIT 1",
        (rs, rowNum) -> new DevelopmentTaskExecutionDetail(
            rs.getLong("id"),
            rs.getLong("node_id"),
            rs.getString("task_name"),
            rs.getString("task_type"),
            rs.getString("trigger_type"),
            rs.getString("runtime_execution_id"),
            rs.getString("status"),
            rs.getString("operator_name"),
            nullableLong(rs.getLong("duration_ms"), rs.wasNull()),
            rs.getString("error_message"),
            rs.getString("content"),
            rs.getString("config_json"),
            parseOutput(rs.getString("output_json")),
            toLocalDateTime(rs.getTimestamp("start_time")),
            toLocalDateTime(rs.getTimestamp("end_time"))),
        id);
    if (records.isEmpty()) throw new IllegalArgumentException("运行记录不存在：" + id);
    return records.get(0);
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
