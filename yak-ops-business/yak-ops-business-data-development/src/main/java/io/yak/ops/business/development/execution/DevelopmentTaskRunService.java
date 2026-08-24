package io.yak.ops.business.development.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRunResult;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.task.DevelopmentTaskDefinitionNormalizer;
import io.yak.ops.business.development.task.DevelopmentTaskNodeResolver;
import io.yak.ops.business.development.task.DevelopmentTaskValidationException;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Executes the current editor definition through the shared Task Runtime. */
@Service
public class DevelopmentTaskRunService {

  private final DevelopmentTaskNodeResolver nodes;
  private final DevelopmentTaskDefinitionNormalizer definitions;
  private final TaskExecutionGateway taskExecutionGateway;
  private final DevelopmentTaskExecutionService executionService;
  private final ObjectMapper objectMapper;

  /** Keeps focused tests and existing non-Spring callers source compatible. */
  public DevelopmentTaskRunService(
      DevelopmentNodeRepository nodeRepository,
      TaskExecutionGateway taskExecutionGateway,
      DevelopmentTaskExecutionService executionService,
      ObjectMapper objectMapper) {
    this(
        new DevelopmentTaskNodeResolver(nodeRepository),
        new DevelopmentTaskDefinitionNormalizer(objectMapper),
        taskExecutionGateway,
        executionService,
        objectMapper);
  }

  @Autowired
  public DevelopmentTaskRunService(
      DevelopmentTaskNodeResolver nodes,
      DevelopmentTaskDefinitionNormalizer definitions,
      TaskExecutionGateway taskExecutionGateway,
      DevelopmentTaskExecutionService executionService,
      ObjectMapper objectMapper) {
    this.nodes = nodes;
    this.definitions = definitions;
    this.taskExecutionGateway = taskExecutionGateway;
    this.executionService = executionService;
    this.objectMapper = objectMapper;
  }

  /** Kept for source-compatible tests and internal callers. */
  public DevelopmentTaskRunResult run(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    return run(nodeId, taskType, schemaVersion, content, configJson, "unknown");
  }

  public DevelopmentTaskRunResult run(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName) {
    DevelopmentNode node = nodes.requireNode(nodeId);
    TaskDefinition definition =
        definitions.normalize(node, taskType, schemaVersion, content, configJson);
    if (!taskExecutionGateway.supports(definition.taskType())) {
      throw new DevelopmentTaskValidationException(
          "当前未安装 " + definition.taskType() + " Task Runtime，无法运行",
          List.of(new TaskValidationIssue(
              "TASK_RUNTIME_NOT_INSTALLED",
              "taskType",
              "Task runtime is not installed: " + definition.taskType())));
    }

    long started = System.nanoTime();
    long historyId = executionService.createPending(
        node,
        definition.taskType(),
        definition.content(),
        definition.configJson(),
        operatorName);
    try {
      TaskVersionSnapshot snapshot = currentDraftSnapshot(node, definition);
      TaskExecution execution = taskExecutionGateway.start(
          snapshot,
          TaskExecutionTrigger.MANUAL,
          null,
          Map.of("nodeId", String.valueOf(nodeId)));
      executionService.markRunning(historyId, execution.executionId());

      TaskExecution completed = awaitTerminal(snapshot.type(), execution);
      TaskExecutionStatus status = mapStatus(completed);
      long durationMs = elapsedMillis(started);
      executionService.complete(
          historyId,
          status.name(),
          durationMs,
          completed.errorMessage(),
          completed.output());
      return new DevelopmentTaskRunResult(
          status,
          completed.errorMessage(),
          durationMs,
          completed.output());
    } catch (DevelopmentTaskValidationException exception) {
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          elapsedMillis(started),
          safeMessage(exception),
          Map.of());
      throw exception;
    } catch (IllegalArgumentException exception) {
      String message = safeMessage(exception);
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          elapsedMillis(started),
          message,
          Map.of());
      throw new DevelopmentTaskValidationException(
          message,
          List.of(new TaskValidationIssue(
              "TASK_RUNTIME_VALIDATION_FAILED",
              "definition",
              message)));
    } catch (Exception exception) {
      String message = safeMessage(exception);
      long durationMs = elapsedMillis(started);
      executionService.complete(
          historyId,
          TaskExecutionStatus.FAILED.name(),
          durationMs,
          message,
          Map.of());
      return new DevelopmentTaskRunResult(
          TaskExecutionStatus.FAILED,
          message,
          durationMs,
          Map.of());
    }
  }

  private TaskVersionSnapshot currentDraftSnapshot(
      DevelopmentNode node,
      TaskDefinition definition) {
    try {
      return new TaskVersionSnapshot(
          "development:" + node.id(),
          node.name(),
          definition.taskType(),
          0L,
          null,
          objectMapper.writeValueAsString(definition),
          definition.configJson());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("当前任务定义无法序列化为运行时快照", exception);
    }
  }

  private TaskExecution awaitTerminal(String taskType, TaskExecution execution) {
    TaskExecution current = execution;
    while (!current.terminal()) {
      try {
        Thread.sleep(10L);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        try {
          taskExecutionGateway.cancel(taskType, current.executionId());
        } catch (RuntimeException ignored) {
          // Preserve the original interruption as the manual-run result.
        }
        throw new IllegalStateException("等待任务执行结果时被中断", exception);
      }
      current = taskExecutionGateway.status(taskType, current.executionId());
    }
    return current;
  }

  private TaskExecutionStatus mapStatus(TaskExecution execution) {
    String status = execution.status() == null
        ? ""
        : execution.status().trim().toUpperCase(Locale.ROOT);
    return switch (status) {
      case "PENDING" -> TaskExecutionStatus.PENDING;
      case "RUNNING" -> TaskExecutionStatus.RUNNING;
      case "SUCCEEDED" -> TaskExecutionStatus.SUCCESS;
      case "CANCELED" -> TaskExecutionStatus.CANCELLED;
      case "TIMED_OUT" -> TaskExecutionStatus.TIMEOUT;
      default -> TaskExecutionStatus.FAILED;
    };
  }

  private long elapsedMillis(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "任务执行失败" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
