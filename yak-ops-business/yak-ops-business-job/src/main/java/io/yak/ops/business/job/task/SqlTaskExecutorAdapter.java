package io.yak.ops.business.job.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Executes SQL revisions through the shared Task Runtime and TaskPlugin contract. */
@Service
public class SqlTaskExecutorAdapter implements TaskExecutor {

  private final TaskPluginRegistry pluginRegistry;
  private final ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider;
  private final ObjectMapper objectMapper;
  private final ExecutorService workerExecutor;
  private final ConcurrentMap<String, ExecutionHandle> executions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

  public SqlTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider,
      ObjectMapper objectMapper) {
    this.pluginRegistry = pluginRegistry;
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
    this.objectMapper = objectMapper;
    this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
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
    return start(snapshot, TaskExecutionTrigger.WORKFLOW, idempotencyKey, input);
  }

  @Override
  public synchronized TaskExecution start(
      TaskVersionSnapshot snapshot,
      TaskExecutionTrigger trigger,
      String idempotencyKey,
      Map<String, Object> input) {
    requireSqlSnapshot(snapshot);
    TaskExecutionTrigger safeTrigger =
        trigger == null ? TaskExecutionTrigger.WORKFLOW : trigger;
    String safeIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    if (safeIdempotencyKey != null) {
      String existingExecutionId = idempotencyIndex.get(safeIdempotencyKey);
      if (existingExecutionId != null) {
        return status(existingExecutionId);
      }
    }

    TaskDefinition definition = immutableDefinition(snapshot);
    TaskPlugin plugin = pluginRegistry.require("SQL");
    if (!plugin.descriptor().executable()) {
      throw new IllegalStateException("SQL Task Plugin 暂不支持执行");
    }
    validate(plugin, definition);

    DataSourceExecutionProvider provider = dataSourceExecutionProvider.getIfAvailable();
    SqlExecutionContext context = new SqlExecutionContext(safeTrigger, input, provider);
    io.yak.ops.plugin.task.api.TaskExecutor pluginExecutor =
        plugin.createExecutor(definition, context);

    String executionId = "sql-" + UUID.randomUUID();
    TaskExecution initial = new TaskExecution(executionId, "RUNNING", null, Map.of());
    ExecutionHandle handle = new ExecutionHandle(pluginExecutor, new AtomicReference<>(initial));
    executions.put(executionId, handle);
    if (safeIdempotencyKey != null) {
      idempotencyIndex.put(safeIdempotencyKey, executionId);
    }

    try {
      workerExecutor.submit(() -> execute(executionId, handle));
    } catch (RuntimeException exception) {
      TaskExecution failed = new TaskExecution(
          executionId,
          "FAILED",
          safeMessage(exception),
          Map.of());
      handle.snapshot().set(failed);
      return failed;
    }
    return initial;
  }

  @Override
  public TaskExecution status(String executionId) {
    ExecutionHandle handle = executions.get(executionId);
    if (handle == null) {
      throw new IllegalArgumentException("SQL 任务执行不存在：" + executionId);
    }
    return handle.snapshot().get();
  }

  @Override
  public void cancel(String executionId) {
    ExecutionHandle handle = executions.get(executionId);
    if (handle == null) {
      throw new IllegalArgumentException("SQL 任务执行不存在：" + executionId);
    }
    TaskExecution current = handle.snapshot().get();
    if (current.terminal()) return;

    handle.executor().cancel();
    handle.snapshot().updateAndGet(existing -> existing.terminal()
        ? existing
        : new TaskExecution(executionId, "CANCELED", "SQL execution cancelled", existing.output()));
  }

  @PreDestroy
  void shutdown() {
    workerExecutor.shutdownNow();
  }

  private void execute(String executionId, ExecutionHandle handle) {
    try {
      TaskExecutionResult result = handle.executor().execute();
      TaskExecution completed = convert(executionId, result);
      handle.snapshot().updateAndGet(current -> current.terminal() ? current : completed);
    } catch (Exception exception) {
      TaskExecution failed = new TaskExecution(
          executionId,
          "FAILED",
          safeMessage(exception),
          Map.of());
      handle.snapshot().updateAndGet(current -> current.terminal() ? current : failed);
    }
  }

  private TaskExecution convert(String executionId, TaskExecutionResult result) {
    String status = switch (result.status()) {
      case PENDING -> "PENDING";
      case RUNNING -> "RUNNING";
      case SUCCESS -> "SUCCEEDED";
      case FAILED -> "FAILED";
      case CANCELLED -> "CANCELED";
      case TIMEOUT -> "TIMED_OUT";
    };
    String errorMessage = result.status() == TaskExecutionStatus.SUCCESS ? null : result.message();
    return new TaskExecution(executionId, status, errorMessage, result.output());
  }

  private TaskDefinition immutableDefinition(TaskVersionSnapshot snapshot) {
    String definitionJson = snapshot.definitionSnapshotJson();
    if (definitionJson == null || definitionJson.isBlank()) {
      throw new IllegalArgumentException(
          "SQL 任务缺少不可变 definitionSnapshot，拒绝回退到当前草稿或最新版本：" + snapshot.taskId());
    }
    try {
      TaskDefinition definition = objectMapper.readValue(definitionJson, TaskDefinition.class);
      if (!"SQL".equalsIgnoreCase(definition.taskType())) {
        throw new IllegalArgumentException(
            "SQL 任务快照类型不匹配：snapshot=SQL, definition=" + definition.taskType());
      }
      return definition;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("SQL 任务不可变 definitionSnapshot 不是合法 JSON", exception);
    }
  }

  private void requireSqlSnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("任务版本快照不能为空");
    }
    if (!"SQL".equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("SQL 执行器不能执行任务类型：" + snapshot.type());
    }
  }

  private void validate(TaskPlugin plugin, TaskDefinition definition) {
    TaskValidationResult validation = plugin.validate(definition);
    if (validation.valid()) return;
    String summary = validation.issues().stream()
        .map(TaskValidationIssue::message)
        .limit(3)
        .reduce((left, right) -> left + "；" + right)
        .orElse("SQL 任务定义校验失败");
    throw new IllegalArgumentException(summary);
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
    return idempotencyKey.trim();
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private record ExecutionHandle(
      io.yak.ops.plugin.task.api.TaskExecutor executor,
      AtomicReference<TaskExecution> snapshot) {
  }

  private record SqlExecutionContext(
      TaskExecutionTrigger trigger,
      Map<String, Object> input,
      DataSourceExecutionProvider dataSourceExecutionProvider)
      implements TaskExecutionContext {

    private SqlExecutionContext {
      trigger = trigger == null ? TaskExecutionTrigger.WORKFLOW : trigger;
      input = input == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    @Override
    public Map<String, Object> parameters() {
      return input;
    }

    @Override
    public <T> Optional<T> capability(Class<T> capabilityType) {
      if (dataSourceExecutionProvider != null
          && capabilityType.isInstance(dataSourceExecutionProvider)) {
        return Optional.of(capabilityType.cast(dataSourceExecutionProvider));
      }
      return Optional.empty();
    }
  }
}
