package io.yak.ops.business.development.service;

import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Definition;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Execution;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Version;
import io.yak.ops.business.development.domain.SqlParameterDefinition;
import io.yak.ops.business.development.domain.SqlTaskSnapshot;
import io.yak.ops.business.development.execution.NamedSqlParameterParser;
import io.yak.ops.business.development.repository.SqlDevelopmentRepository;
import io.yak.ops.business.development.support.SqlDevelopmentJsonCodec;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SQL development application service: draft, immutable publish and direct test run. */
@Service
public class SqlDevelopmentService {

  private static final Set<String> PARAMETER_TYPES = Set.of(
      "STRING", "INTEGER", "LONG", "DOUBLE", "DECIMAL", "BOOLEAN", "DATE", "TIMESTAMP");

  private final SqlDevelopmentRepository repository;
  private final DataSourceRepository dataSourceRepository;
  private final SqlDevelopmentJsonCodec jsonCodec;
  private final TaskExecutionGateway executionGateway;

  public SqlDevelopmentService(
      SqlDevelopmentRepository repository,
      DataSourceRepository dataSourceRepository,
      SqlDevelopmentJsonCodec jsonCodec,
      TaskExecutionGateway executionGateway) {
    this.repository = repository;
    this.dataSourceRepository = dataSourceRepository;
    this.jsonCodec = jsonCodec;
    this.executionGateway = executionGateway;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Definition create(
      String name,
      String description,
      Long projectId,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters) {
    Long normalizedProjectId = normalizeProjectId(projectId);
    List<SqlParameterDefinition> normalized = validate(dataSourceId, sql, parameters);
    return repository.insertDefinition(
        requireText(name, "任务名称"),
        trimToNull(description),
        normalizedProjectId,
        dataSourceId,
        sql.trim(),
        normalized);
  }

  public List<Definition> list() {
    return list(null);
  }

  public List<Definition> list(Long projectId) {
    if (projectId == null) return repository.listDefinitions();
    return repository.listDefinitions(requirePositive(projectId, "项目 ID"));
  }

  public Definition get(Long id) {
    return requireDefinition(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Definition update(
      Long id,
      long baseRevision,
      String name,
      String description,
      Long projectId,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters) {
    Definition current = requireDefinition(id);
    Long normalizedProjectId = projectId == null ? current.projectId() : normalizeProjectId(projectId);
    List<SqlParameterDefinition> normalized = validate(dataSourceId, sql, parameters);
    boolean updated = repository.updateDraft(
        id,
        baseRevision,
        requireText(name, "任务名称"),
        trimToNull(description),
        normalizedProjectId,
        dataSourceId,
        sql.trim(),
        normalized);
    if (!updated) {
      throw new IllegalStateException("SQL 草稿已经被其他请求修改，请刷新后重试");
    }
    return requireDefinition(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Version publish(Long id, long draftRevision) {
    Definition definition = repository.lockDefinition(id)
        .orElseThrow(() -> new IllegalArgumentException("SQL 任务不存在：" + id));
    if (definition.draftRevision() != draftRevision) {
      throw new IllegalStateException(
          "SQL 草稿版本冲突，当前 revision=" + definition.draftRevision()
              + "，请求 revision=" + draftRevision);
    }
    List<SqlParameterDefinition> normalized =
        validate(definition.dataSourceId(), definition.sql(), definition.parameters());
    int versionNo = definition.latestVersionNo() + 1;
    String digest = jsonCodec.digest(definition.sql(), normalized, definition.dataSourceId());
    Version version = repository.insertVersion(
        id,
        versionNo,
        definition.dataSourceId(),
        definition.sql(),
        normalized,
        digest);
    if (!repository.markPublished(id, version.id(), versionNo)) {
      throw new IllegalStateException("SQL 发布版本写入成功，但任务发布指针更新失败：" + id);
    }
    return version;
  }

  public List<Version> versions(Long id) {
    requireDefinition(id);
    return repository.listVersions(id);
  }

  public Execution runDraft(Long id, Map<String, Object> input) {
    Definition definition = requireDefinition(id);
    List<SqlParameterDefinition> normalized =
        validate(definition.dataSourceId(), definition.sql(), definition.parameters());
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        workflowTaskId(id),
        definition.name(),
        "SQL",
        0L,
        jsonCodec.digest(definition.sql(), normalized, definition.dataSourceId()),
        jsonCodec.write(new SqlTaskSnapshot.Definition(definition.sql(), normalized)),
        jsonCodec.write(new SqlTaskSnapshot.ExecutionConfig(definition.dataSourceId(), null)));
    TaskExecution started = executionGateway.start(
        snapshot,
        "manual:" + UUID.randomUUID(),
        input == null ? Map.of() : input);
    return execution(Long.parseLong(started.executionId()));
  }

  public Execution execution(Long executionId) {
    Execution current = repository.findExecution(executionId)
        .orElseThrow(() -> new IllegalArgumentException("SQL 执行不存在：" + executionId));
    if (!isTerminal(current.status())) {
      executionGateway.status("SQL", String.valueOf(executionId));
      current = repository.findExecution(executionId)
          .orElseThrow(() -> new IllegalArgumentException("SQL 执行不存在：" + executionId));
    }
    return current;
  }

  public Execution cancel(Long executionId) {
    Execution execution = execution(executionId);
    if (!isTerminal(execution.status())) {
      executionGateway.cancel("SQL", String.valueOf(executionId));
    }
    return execution(executionId);
  }

  private List<SqlParameterDefinition> validate(
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters) {
    if (dataSourceId == null || dataSourceId <= 0L
        || dataSourceRepository.findById(dataSourceId).isEmpty()) {
      throw new IllegalArgumentException("数据源不存在：" + dataSourceId);
    }
    NamedSqlParameterParser.ParsedSql parsed = NamedSqlParameterParser.parse(sql);
    List<SqlParameterDefinition> normalized = normalizeParameters(parameters);
    Set<String> declared = new HashSet<>();
    for (SqlParameterDefinition parameter : normalized) declared.add(parameter.name());
    for (String name : parsed.parameterNames()) {
      if (!declared.contains(name)) {
        throw new IllegalArgumentException("SQL 参数未声明：" + name);
      }
    }
    return normalized;
  }

  private List<SqlParameterDefinition> normalizeParameters(List<SqlParameterDefinition> parameters) {
    if (parameters == null || parameters.isEmpty()) return List.of();
    Set<String> names = new HashSet<>();
    return parameters.stream().map(parameter -> {
      if (parameter == null) throw new IllegalArgumentException("SQL 参数定义不能为空");
      String name = requireText(parameter.name(), "参数名称");
      if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        throw new IllegalArgumentException("SQL 参数名称不合法：" + name);
      }
      if (!names.add(name)) throw new IllegalArgumentException("SQL 参数重复：" + name);
      String type = parameter.type() == null || parameter.type().isBlank()
          ? "STRING"
          : parameter.type().trim().toUpperCase(Locale.ROOT);
      if (!PARAMETER_TYPES.contains(type)) {
        throw new IllegalArgumentException("不支持的 SQL 参数类型：" + type);
      }
      return new SqlParameterDefinition(name, type, parameter.required(), parameter.defaultValue());
    }).toList();
  }

  private Definition requireDefinition(Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("SQL 任务 ID 不合法：" + id);
    return repository.findDefinition(id)
        .orElseThrow(() -> new IllegalArgumentException("SQL 任务不存在：" + id));
  }

  private Long normalizeProjectId(Long value) {
    return value == null ? null : requirePositive(value, "项目 ID");
  }

  private long requirePositive(Long value, String name) {
    if (value == null || value <= 0L) throw new IllegalArgumentException(name + "不合法：" + value);
    return value;
  }

  private String workflowTaskId(Long id) {
    return "SQL:" + id;
  }

  private String requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
    return value.trim();
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean isTerminal(String status) {
    return "SUCCEEDED".equalsIgnoreCase(status)
        || "FAILED".equalsIgnoreCase(status)
        || "CANCELED".equalsIgnoreCase(status)
        || "TIMED_OUT".equalsIgnoreCase(status)
        || "LOST".equalsIgnoreCase(status);
  }
}
