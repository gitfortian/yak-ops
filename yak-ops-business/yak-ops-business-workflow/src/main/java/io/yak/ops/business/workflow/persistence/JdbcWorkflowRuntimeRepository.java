package io.yak.ops.business.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC runtime index used for history reads, restart scanning and external task reconciliation. */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcWorkflowRuntimeRepository implements WorkflowRuntimePersistence {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowRuntimeRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public void prepareMetadata(String definitionId, RuntimeMetadataRecord metadata) {
    int updated = jdbc.update(
        "UPDATE yak_workflow_version SET runtime_metadata_json=? WHERE id=?",
        write(metadata),
        definitionId);
    if (updated == 0) {
      throw new IllegalArgumentException("工作流运行定义不存在：" + definitionId);
    }
  }

  @Override
  public void saveMetadata(String executionId, RuntimeMetadataRecord metadata) {
    int updated = jdbc.update(
        "UPDATE yak_workflow_execution SET workflow_name=?,workflow_version_id=?,"
            + "workflow_version_no=?,test_run=?,edge_count=?,workflow_timeout_seconds=?,"
            + "failure_strategy=?,runtime_metadata_json=? WHERE id=?",
        metadata.name(),
        metadata.workflowVersionId(),
        metadata.workflowVersionNo(),
        metadata.testRun(),
        metadata.edgeCount(),
        metadata.workflowTimeoutSeconds(),
        metadata.failureStrategy(),
        write(metadata),
        executionId);
    if (updated == 0) {
      throw new IllegalArgumentException("工作流执行实例不存在：" + executionId);
    }
  }

  @Override
  public Optional<RuntimeMetadataRecord> findMetadata(String executionId) {
    try {
      String json = jdbc.queryForObject(
          "SELECT COALESCE(e.runtime_metadata_json,v.runtime_metadata_json) "
              + "FROM yak_workflow_execution e "
              + "LEFT JOIN yak_workflow_version v ON v.id=e.definition_id WHERE e.id=?",
          String.class,
          executionId);
      if (json == null || json.isBlank()) return Optional.empty();
      return Optional.of(read(json));
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  @Override
  public List<String> listExecutionIds() {
    return jdbc.queryForList(
        "SELECT id FROM yak_workflow_execution ORDER BY created_at DESC",
        String.class);
  }

  @Override
  public List<String> findRecoverableExecutionIds() {
    return jdbc.queryForList(
        "SELECT id FROM yak_workflow_execution WHERE status IN "
            + "('CREATED','RUNNING','PAUSING','PAUSED','RESUMING') ORDER BY created_at",
        String.class);
  }

  @Override
  public void bindExternalExecution(String attemptId, String externalExecutionId) {
    if (attemptId == null || attemptId.isBlank()
        || externalExecutionId == null || externalExecutionId.isBlank()) {
      throw new IllegalArgumentException("attemptId 和 externalExecutionId 不能为空");
    }
    int updated = jdbc.update(
        "UPDATE yak_workflow_node_attempt SET external_execution_id=? WHERE id=? "
            + "AND (external_execution_id IS NULL OR external_execution_id=?)",
        externalExecutionId,
        attemptId,
        externalExecutionId);
    if (updated == 0) {
      String existing = findExternalExecution(attemptId).orElse(null);
      if (existing == null) {
        throw new IllegalArgumentException("工作流 Attempt 不存在：" + attemptId);
      }
      if (!externalExecutionId.equals(existing)) {
        throw new IllegalStateException(
            "工作流 Attempt 已绑定其他远端执行：" + attemptId + " -> " + existing);
      }
    }
  }

  @Override
  public Optional<String> findExternalExecution(String attemptId) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(
          "SELECT external_execution_id FROM yak_workflow_node_attempt WHERE id=?",
          String.class,
          attemptId));
    } catch (EmptyResultDataAccessException ignored) {
      return Optional.empty();
    }
  }

  private String write(RuntimeMetadataRecord metadata) {
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化工作流 Runtime Metadata 失败", exception);
    }
  }

  private RuntimeMetadataRecord read(String json) {
    try {
      return objectMapper.readValue(json, RuntimeMetadataRecord.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("读取工作流 Runtime Metadata 失败", exception);
    }
  }
}
