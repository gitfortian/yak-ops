package io.yak.ops.business.development.execution;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Execution;
import io.yak.ops.business.development.domain.SqlParameterDefinition;
import io.yak.ops.business.development.domain.SqlTaskSnapshot;
import io.yak.ops.business.development.repository.SqlDevelopmentRepository;
import io.yak.ops.business.development.support.SqlDevelopmentJsonCodec;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** First-phase JDBC SQL executor. It is one plugin behind the generic Job execution gateway. */
@Component
public class SqlTaskExecutor implements TaskExecutor {

  private static final int MAX_RESULT_ROWS = 200;
  private static final int MAX_CELL_TEXT_LENGTH = 10_000;

  private final SqlDevelopmentRepository repository;
  private final DataSourceRepository dataSourceRepository;
  private final DataSourcePluginRegistry pluginRegistry;
  private final SqlDevelopmentJsonCodec jsonCodec;
  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
  private final ConcurrentMap<String, RunningControl> running = new ConcurrentHashMap<>();

  public SqlTaskExecutor(
      SqlDevelopmentRepository repository,
      DataSourceRepository dataSourceRepository,
      DataSourcePluginRegistry pluginRegistry,
      SqlDevelopmentJsonCodec jsonCodec) {
    this.repository = repository;
    this.dataSourceRepository = dataSourceRepository;
    this.pluginRegistry = pluginRegistry;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public String taskType() {
    return "SQL";
  }

  @Override
  public TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input) {
    requireSqlSnapshot(snapshot);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Execution existing = repository.findExecutionByIdempotencyKey(idempotencyKey).orElse(null);
      if (existing != null) return toTaskExecution(existing);
    }

    SqlTaskSnapshot.Definition definition =
        jsonCodec.read(snapshot.definitionSnapshotJson(), SqlTaskSnapshot.Definition.class);
    SqlTaskSnapshot.ExecutionConfig config =
        jsonCodec.read(snapshot.executionConfigSnapshotJson(), SqlTaskSnapshot.ExecutionConfig.class);
    NamedSqlParameterParser.ParsedSql parsed = NamedSqlParameterParser.parse(definition.sql());
    validateDeclaredParameters(parsed.parameterNames(), definition.parameters());
    Long taskId = parseTaskId(snapshot.taskId());
    Integer versionNo = snapshot.version() > 0L ? Math.toIntExact(snapshot.version()) : null;

    Execution execution;
    try {
      execution = repository.insertExecution(
          taskId,
          config.taskVersionId(),
          versionNo,
          requireDataSourceId(config.dataSourceId()),
          definition.sql(),
          input,
          idempotencyKey);
    } catch (DataIntegrityViolationException duplicate) {
      if (idempotencyKey == null || idempotencyKey.isBlank()) throw duplicate;
      execution = repository.findExecutionByIdempotencyKey(idempotencyKey).orElseThrow(() -> duplicate);
      return toTaskExecution(execution);
    }

    String executionId = String.valueOf(execution.id());
    RunningControl control = new RunningControl();
    RunningControl duplicateControl = running.putIfAbsent(executionId, control);
    if (duplicateControl != null) return toTaskExecution(execution);
    control.future = executorService.submit(
        () -> execute(execution.id(), config.dataSourceId(), parsed, definition.parameters(), input, control));
    return toTaskExecution(execution);
  }

  @Override
  public TaskExecution status(String executionId) {
    Long id = parseExecutionId(executionId);
    Execution execution = requireExecution(id);
    if (isActive(execution.status()) && !running.containsKey(executionId)) {
      repository.markLost(id, "SQL 执行进程已丢失；当前版本不会在进程重启后自动重复提交 SQL");
      execution = requireExecution(id);
    }
    return toTaskExecution(execution);
  }

  @Override
  public void cancel(String executionId) {
    Long id = parseExecutionId(executionId);
    RunningControl control = running.get(executionId);
    if (control != null) {
      control.canceled.set(true);
      PreparedStatement statement = control.statement;
      if (statement != null) {
        try {
          statement.cancel();
        } catch (Exception ignored) {
          // State transition below remains the local source of truth.
        }
      }
      Future<?> future = control.future;
      if (future != null) future.cancel(true);
    }
    repository.markCanceled(id);
  }

  private void execute(
      Long executionId,
      Long dataSourceId,
      NamedSqlParameterParser.ParsedSql parsed,
      List<SqlParameterDefinition> parameters,
      Map<String, Object> input,
      RunningControl control) {
    String key = String.valueOf(executionId);
    try {
      if (control.canceled.get()) {
        repository.markCanceled(executionId);
        return;
      }
      if (!repository.markRunning(executionId)) return;

      try (Connection connection = openConnection(dataSourceId);
           PreparedStatement statement = connection.prepareStatement(parsed.jdbcSql())) {
        connection.setAutoCommit(true);
        control.statement = statement;
        bind(statement, parsed.parameterNames(), parameters, input);
        boolean hasResult = statement.execute();
        if (control.canceled.get()) {
          repository.markCanceled(executionId);
          return;
        }

        SqlResult result = hasResult
            ? readResultSet(statement.getResultSet())
            : updateResult(statement.getUpdateCount());
        repository.markSucceeded(executionId, result.affectedRows(), result.output());
      }
    } catch (Exception exception) {
      if (control.canceled.get() || Thread.currentThread().isInterrupted()) {
        repository.markCanceled(executionId);
      } else {
        repository.markFailed(executionId, conciseMessage(exception));
      }
    } finally {
      control.statement = null;
      running.remove(key, control);
    }
  }

  private Connection openConnection(Long dataSourceId) throws Exception {
    DataSourceDefinition definition = dataSourceRepository.findById(dataSourceId)
        .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceId));
    DataSourcePlugin plugin = pluginRegistry.get(definition.getDbType());
    DataSourceConnection connection = plugin.parseConnection(definition.getConnectionParams());
    if (connection.driverClassName() != null && !connection.driverClassName().isBlank()) {
      Class.forName(connection.driverClassName());
    }
    Properties properties = new Properties();
    if (connection.properties() != null) properties.putAll(connection.properties());
    if (connection.username() != null) properties.setProperty("user", connection.username());
    if (connection.password() != null) properties.setProperty("password", connection.password());
    return DriverManager.getConnection(connection.jdbcUrl(), properties);
  }

  private void bind(
      PreparedStatement statement,
      List<String> parameterNames,
      List<SqlParameterDefinition> definitions,
      Map<String, Object> input) throws Exception {
    Map<String, SqlParameterDefinition> definitionsByName = new HashMap<>();
    for (SqlParameterDefinition definition : definitions) {
      definitionsByName.put(definition.name(), definition);
    }
    Map<String, Object> values = input == null ? Map.of() : input;
    for (int index = 0; index < parameterNames.size(); index++) {
      String name = parameterNames.get(index);
      SqlParameterDefinition definition = definitionsByName.get(name);
      if (definition == null) throw new IllegalArgumentException("SQL 参数未声明：" + name);
      boolean supplied = values.containsKey(name);
      Object value = supplied ? values.get(name) : definition.defaultValue();
      if (value == null && definition.required()) {
        throw new IllegalArgumentException("缺少必填 SQL 参数：" + name);
      }
      statement.setObject(index + 1, convertValue(definition.type(), value));
    }
  }

  private Object convertValue(String type, Object value) {
    if (value == null) return null;
    String normalized = type == null ? "STRING" : type.trim().toUpperCase(Locale.ROOT);
    String text = String.valueOf(value);
    return switch (normalized) {
      case "STRING" -> text;
      case "INTEGER" -> value instanceof Number number ? number.intValue() : Integer.valueOf(text);
      case "LONG" -> value instanceof Number number ? number.longValue() : Long.valueOf(text);
      case "DOUBLE" -> value instanceof Number number ? number.doubleValue() : Double.valueOf(text);
      case "DECIMAL" -> value instanceof BigDecimal decimal ? decimal : new BigDecimal(text);
      case "BOOLEAN" -> value instanceof Boolean bool ? bool : parseBoolean(text);
      case "DATE" -> value instanceof LocalDate date ? date : LocalDate.parse(text);
      case "TIMESTAMP" -> value instanceof LocalDateTime dateTime ? dateTime : LocalDateTime.parse(text);
      default -> throw new IllegalArgumentException("不支持的 SQL 参数类型：" + normalized);
    };
  }

  private Boolean parseBoolean(String value) {
    if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("BOOLEAN 参数值不合法：" + value);
  }

  private SqlResult readResultSet(ResultSet resultSet) throws Exception {
    if (resultSet == null) return new SqlResult(0L, Map.of("kind", "QUERY", "rows", List.of()));
    ResultSetMetaData metadata = resultSet.getMetaData();
    int columnCount = metadata.getColumnCount();
    List<String> columns = new ArrayList<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      String label = metadata.getColumnLabel(i);
      columns.add(label == null || label.isBlank() ? metadata.getColumnName(i) : label);
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    boolean truncated = false;
    while (resultSet.next()) {
      if (rows.size() >= MAX_RESULT_ROWS) {
        truncated = true;
        break;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        row.put(columns.get(i - 1), normalizeJdbcValue(resultSet.getObject(i)));
      }
      rows.add(row);
    }
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("kind", "QUERY");
    output.put("columns", columns);
    output.put("rows", rows);
    output.put("rowCount", rows.size());
    output.put("truncated", truncated);
    return new SqlResult(0L, output);
  }

  private SqlResult updateResult(int updateCount) {
    long affectedRows = Math.max(0, updateCount);
    return new SqlResult(
        affectedRows,
        Map.of("kind", "UPDATE", "affectedRows", affectedRows));
  }

  private Object normalizeJdbcValue(Object value) throws Exception {
    if (value == null) return null;
    if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
    if (value instanceof Clob clob) {
      long length = Math.min(clob.length(), MAX_CELL_TEXT_LENGTH);
      return clob.getSubString(1L, (int) length);
    }
    if (value instanceof Blob blob) {
      int length = (int) Math.min(blob.length(), MAX_CELL_TEXT_LENGTH);
      return Base64.getEncoder().encodeToString(blob.getBytes(1L, length));
    }
    if (value instanceof CharSequence text && text.length() > MAX_CELL_TEXT_LENGTH) {
      return text.subSequence(0, MAX_CELL_TEXT_LENGTH).toString();
    }
    if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence
        || value instanceof LocalDate || value instanceof LocalDateTime) {
      return value;
    }
    return String.valueOf(value);
  }

  private void validateDeclaredParameters(
      List<String> usedParameters,
      List<SqlParameterDefinition> definitions) {
    Map<String, SqlParameterDefinition> byName = new HashMap<>();
    for (SqlParameterDefinition definition : definitions) {
      if (definition == null || definition.name() == null || definition.name().isBlank()) {
        throw new IllegalArgumentException("SQL 参数定义不完整");
      }
      if (byName.putIfAbsent(definition.name(), definition) != null) {
        throw new IllegalArgumentException("SQL 参数重复：" + definition.name());
      }
    }
    for (String name : usedParameters) {
      if (!byName.containsKey(name)) throw new IllegalArgumentException("SQL 参数未声明：" + name);
    }
  }

  private void requireSqlSnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null || !"SQL".equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("SqlTaskExecutor 仅支持 SQL 任务快照");
    }
    if (snapshot.definitionSnapshotJson() == null || snapshot.executionConfigSnapshotJson() == null) {
      throw new IllegalArgumentException("SQL 任务快照不完整：" + snapshot.taskId());
    }
  }

  private Long requireDataSourceId(Long dataSourceId) {
    if (dataSourceId == null || dataSourceId <= 0L) {
      throw new IllegalArgumentException("SQL 快照缺少有效 dataSourceId");
    }
    return dataSourceId;
  }

  private Long parseTaskId(String taskId) {
    if (taskId == null || !taskId.startsWith("SQL:")) {
      throw new IllegalArgumentException("SQL taskId 不合法：" + taskId);
    }
    try {
      long id = Long.parseLong(taskId.substring(4));
      if (id <= 0L) throw new NumberFormatException(taskId);
      return id;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("SQL taskId 不合法：" + taskId, exception);
    }
  }

  private Long parseExecutionId(String executionId) {
    try {
      long id = Long.parseLong(executionId);
      if (id <= 0L) throw new NumberFormatException(executionId);
      return id;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("SQL executionId 不合法：" + executionId, exception);
    }
  }

  private Execution requireExecution(Long id) {
    return repository.findExecution(id)
        .orElseThrow(() -> new IllegalArgumentException("SQL 执行不存在：" + id));
  }

  private TaskExecution toTaskExecution(Execution execution) {
    return new TaskExecution(
        String.valueOf(execution.id()),
        execution.status(),
        execution.errorMessage(),
        execution.output());
  }

  private boolean isActive(String status) {
    return "QUEUED".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status);
  }

  private String conciseMessage(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
    return message.length() <= 4000 ? message : message.substring(0, 4000);
  }

  @PreDestroy
  void shutdown() {
    executorService.shutdownNow();
  }

  private record SqlResult(long affectedRows, Map<String, Object> output) {}

  private static final class RunningControl {
    private final AtomicBoolean canceled = new AtomicBoolean();
    private volatile PreparedStatement statement;
    private volatile Future<?> future;
  }
}
