package io.yak.ops.business.job.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes Python revisions through the shared Task Runtime and TaskPlugin contract. */
@Service
public class PythonTaskExecutorAdapter implements TaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(PythonTaskExecutorAdapter.class);
  private static final String TYPE = "PYTHON";

  private final TaskPluginRegistry pluginRegistry;
  private final ObjectMapper objectMapper;
  private final TaskExecutionContextFactory contextFactory;
  private final ExecutorService workerExecutor;
  private final ConcurrentMap<String, ExecutionHandle> executions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

  public PythonTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    this.pluginRegistry = pluginRegistry;
    this.objectMapper = objectMapper;
    this.contextFactory = contextFactory;
    this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public String taskType() {
    return TYPE;
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
    requirePythonSnapshot(snapshot);
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
    TaskPlugin plugin = pluginRegistry.require(TYPE);
    if (!plugin.descriptor().executable()) {
      throw new IllegalStateException("Python Task Plugin 暂不支持执行");
    }
    validate(plugin, definition);

    TaskExecutionContext context = contextFactory.create(safeTrigger, input);
    io.yak.ops.plugin.task.api.TaskExecutor pluginExecutor =
        plugin.createExecutor(definition, context);

    String executionId = "python-" + UUID.randomUUID();
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
      throw new IllegalArgumentException("Python 任务执行不存在：" + executionId);
    }
    return handle.snapshot().get();
  }

  @Override
  public void cancel(String executionId) {
    ExecutionHandle handle = executions.get(executionId);
    if (handle == null) {
      throw new IllegalArgumentException("Python 任务执行不存在：" + executionId);
    }
    TaskExecution current = handle.snapshot().get();
    if (current.terminal()) return;

    handle.executor().cancel();
    handle.snapshot().updateAndGet(existing -> existing.terminal()
        ? existing
        : new TaskExecution(executionId, "CANCELED", "Python execution cancelled", existing.output()));
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
      if (completed.terminal() && !"SUCCEEDED".equals(completed.status())) {
        log.warn("Python 任务执行结束 [{}] status={} message={}",
            executionId, completed.status(), completed.errorMessage());
      } else {
        log.info("Python 任务执行完成 [{}] status={}", executionId, completed.status());
      }
    } catch (Exception exception) {
      log.error("Python 任务执行异常 [{}]", executionId, exception);
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
          "Python 任务缺少不可变 definitionSnapshot，拒绝回退到当前草稿或最新版本：" + snapshot.taskId());
    }
    try {
      TaskDefinition definition = objectMapper.readValue(definitionJson, TaskDefinition.class);
      if (!TYPE.equalsIgnoreCase(definition.taskType())) {
        throw new IllegalArgumentException(
            "Python 任务快照类型不匹配：snapshot=PYTHON, definition=" + definition.taskType());
      }
      return definition;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Python 任务不可变 definitionSnapshot 不是合法 JSON", exception);
    }
  }

  private void requirePythonSnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("任务版本快照不能为空");
    }
    if (!TYPE.equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("Python 执行器不能执行任务类型：" + snapshot.type());
    }
  }

  private void validate(TaskPlugin plugin, TaskDefinition definition) {
    TaskValidationResult validation = plugin.validate(definition);
    if (validation.valid()) return;
    String summary = validation.issues().stream()
        .map(TaskValidationIssue::message)
        .limit(3)
        .reduce((left, right) -> left + "；" + right)
        .orElse("Python 任务定义校验失败");
    throw new IllegalArgumentException(summary);
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
    return idempotencyKey.trim();
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "Python execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private record ExecutionHandle(
      io.yak.ops.plugin.task.api.TaskExecutor executor,
      AtomicReference<TaskExecution> snapshot) {
  }
}
