package io.yak.ops.business.job.task;

import static io.yak.ops.plugin.task.api.ScriptTaskSupport.hasResourceReference;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.safeMessage;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.summarizeIssues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.DefaultTaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.resource.ResourceResolver;
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
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Template-method base class for task executor adapters that delegate to the
 * shared Task Runtime / TaskPlugin contract.
 *
 * <p>Subclasses only specify <em>what</em> task type they handle and
 * <em>how</em> the execution context should be configured. All orchestration
 * (idempotency, lifecycle, conversion, error handling) lives here exactly once.</p>
 */
abstract class AbstractTaskExecutorAdapter implements TaskExecutor {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final TaskPluginRegistry pluginRegistry;
  private final ObjectProvider<ResourceResolver> resourceResolverProvider;
  private final ObjectMapper objectMapper;
  private final TaskExecutionContextFactory contextFactory;
  private final ExecutorService workerExecutor;
  private final ConcurrentMap<String, ExecutionHandle> executions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

  protected AbstractTaskExecutorAdapter(
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<ResourceResolver> resourceResolverProvider,
      ObjectMapper objectMapper,
      TaskExecutionContextFactory contextFactory) {
    this.pluginRegistry = pluginRegistry;
    this.resourceResolverProvider = resourceResolverProvider;
    this.objectMapper = objectMapper;
    this.contextFactory = contextFactory;
    this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  // ── Template hooks ────────────────────────────────────────────────

  /**
   * The task type this adapter handles, e.g. "PYTHON", "JAVA", "SHELL".
   * Also serves as the {@link TaskExecutor#taskType()} implementation.
   */
  @Override
  public abstract String taskType();

  /** Prefix for generated execution IDs, e.g. "python", "java", "shell". */
  protected abstract String executionIdPrefix();

  /** Human-readable display name for log/error messages, e.g. "Python". */
  protected abstract String displayName();

  /**
   * Configure the execution context with task-type-specific capabilities.
   *
   * <p>The default injects {@link ResourceResolver} conditionally when the
   * definition carries a {@code resourceId} reference. Override for types
   * that always (or never) require a resolver.</p>
   */
  protected void configureContext(
      DefaultTaskExecutionContext.Builder builder,
      String definitionJson) {
    if (hasResourceReference(definitionJson, objectMapper)) {
      ResourceResolver resolver = resourceResolverProvider.getIfAvailable();
      if (resolver != null) {
        builder.capability(ResourceResolver.class, resolver);
      }
    }
  }

  // ── TaskExecutor implementation ───────────────────────────────────

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
    requireSnapshot(snapshot);

    TaskExecutionTrigger safeTrigger =
        trigger == null ? TaskExecutionTrigger.WORKFLOW : trigger;
    String safeKey = normalizeKey(idempotencyKey);
    if (safeKey != null) {
      String existing = idempotencyIndex.get(safeKey);
      if (existing != null) return status(existing);
    }

    TaskDefinition definition = extractDefinition(snapshot);
    TaskPlugin plugin = requireExecutablePlugin();
    validateDefinition(plugin, definition);

    TaskExecutionContext context = contextFactory.create(
        safeTrigger, input,
        builder -> configureContext(builder, snapshot.definitionSnapshotJson()));

    io.yak.ops.plugin.task.api.TaskExecutor pluginExecutor =
        plugin.createExecutor(definition, context);

    String executionId = executionIdPrefix() + "-" + UUID.randomUUID();
    TaskExecution initial = new TaskExecution(executionId, "RUNNING", null, Map.of());
    ExecutionHandle handle = new ExecutionHandle(pluginExecutor, new AtomicReference<>(initial));
    executions.put(executionId, handle);
    if (safeKey != null) idempotencyIndex.put(safeKey, executionId);

    try {
      workerExecutor.submit(() -> runExecution(executionId, handle));
    } catch (RuntimeException e) {
      TaskExecution failed = new TaskExecution(
          executionId, "FAILED", safeMessage(e, displayName() + " execution failed"), Map.of());
      handle.snapshot().set(failed);
      return failed;
    }
    return initial;
  }

  @Override
  public TaskExecution status(String executionId) {
    ExecutionHandle handle = requireHandle(executionId);
    return handle.snapshot().get();
  }

  @Override
  public void cancel(String executionId) {
    ExecutionHandle handle = requireHandle(executionId);
    TaskExecution current = handle.snapshot().get();
    if (current.terminal()) return;

    handle.executor().cancel();
    handle.snapshot().updateAndGet(existing -> existing.terminal()
        ? existing
        : new TaskExecution(
            executionId, "CANCELED",
            displayName() + " execution cancelled", existing.output()));
  }

  @PreDestroy
  void shutdown() {
    workerExecutor.shutdownNow();
  }

  // ── Protected accessors for subclasses ────────────────────────────

  /** Access the ResourceResolver provider, e.g. for always-required injection. */
  protected ResourceResolver resourceResolver() {
    return resourceResolverProvider.getIfAvailable();
  }

  // ── Internal orchestration ────────────────────────────────────────

  private void runExecution(String executionId, ExecutionHandle handle) {
    try {
      TaskExecutionResult result = handle.executor().execute();
      TaskExecution completed = convert(executionId, result);
      handle.snapshot().updateAndGet(c -> c.terminal() ? c : completed);
      if (completed.terminal() && !"SUCCEEDED".equals(completed.status())) {
        log.warn("{} task execution ended [{}] status={} message={}",
            displayName(), executionId, completed.status(), completed.errorMessage());
      } else {
        log.info("{} task execution completed [{}] status={}",
            displayName(), executionId, completed.status());
      }
    } catch (Exception e) {
      log.error("{} task execution exception [{}]", displayName(), executionId, e);
      TaskExecution failed = new TaskExecution(
          executionId, "FAILED", safeMessage(e, displayName() + " execution failed"), Map.of());
      handle.snapshot().updateAndGet(c -> c.terminal() ? c : failed);
    }
  }

  private static TaskExecution convert(String executionId, TaskExecutionResult result) {
    String status = switch (result.status()) {
      case PENDING -> "PENDING";
      case RUNNING -> "RUNNING";
      case SUCCESS -> "SUCCEEDED";
      case FAILED -> "FAILED";
      case CANCELLED -> "CANCELED";
      case TIMEOUT -> "TIMED_OUT";
    };
    String errorMessage =
        result.status() == TaskExecutionStatus.SUCCESS ? null : result.message();
    return new TaskExecution(executionId, status, errorMessage, result.output());
  }

  // ── Helpers ───────────────────────────────────────────────────────

  private TaskPlugin requireExecutablePlugin() {
    TaskPlugin plugin = pluginRegistry.require(taskType());
    if (!plugin.descriptor().executable()) {
      throw new IllegalStateException(
          displayName() + " Task Plugin does not support execution");
    }
    return plugin;
  }

  private TaskDefinition extractDefinition(TaskVersionSnapshot snapshot) {
    String json = snapshot.definitionSnapshotJson();
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException(
          displayName() + " task missing immutable definitionSnapshot: " + snapshot.taskId());
    }
    try {
      TaskDefinition definition = objectMapper.readValue(json, TaskDefinition.class);
      if (!taskType().equalsIgnoreCase(definition.taskType())) {
        throw new IllegalArgumentException(
            displayName() + " task snapshot type mismatch: snapshot="
                + taskType() + ", definition=" + definition.taskType());
      }
      return definition;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          displayName() + " task definitionSnapshot is not valid JSON", e);
    }
  }

  private void validateDefinition(TaskPlugin plugin, TaskDefinition definition) {
    TaskValidationResult validation = plugin.validate(definition);
    String summary = summarizeIssues(validation,
        displayName() + " task validation failed");
    if (summary != null) throw new IllegalArgumentException(summary);
  }

  private void requireSnapshot(TaskVersionSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Task version snapshot must not be null");
    }
    if (!taskType().equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException(
          displayName() + " executor cannot execute task type: " + snapshot.type());
    }
  }

  private ExecutionHandle requireHandle(String executionId) {
    ExecutionHandle handle = executions.get(executionId);
    if (handle == null) {
      throw new IllegalArgumentException(
          displayName() + " task execution not found: " + executionId);
    }
    return handle;
  }

  private static String normalizeKey(String key) {
    if (key == null || key.isBlank()) return null;
    return key.trim();
  }

  // ── Inner types ───────────────────────────────────────────────────

  protected record ExecutionHandle(
      io.yak.ops.plugin.task.api.TaskExecutor executor,
      AtomicReference<TaskExecution> snapshot) {}
}
