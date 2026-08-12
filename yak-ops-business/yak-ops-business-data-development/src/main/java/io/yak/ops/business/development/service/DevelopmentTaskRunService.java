package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRunResult;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
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
import org.springframework.stereotype.Service;

/** Executes the current editor definition through the shared Task Runtime. */
@Service
public class DevelopmentTaskRunService {

  private final DevelopmentNodeRepository nodeRepository;
  private final TaskExecutionGateway taskExecutionGateway;
  private final ObjectMapper objectMapper;

  public DevelopmentTaskRunService(
      DevelopmentNodeRepository nodeRepository,
      TaskExecutionGateway taskExecutionGateway,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.taskExecutionGateway = taskExecutionGateway;
    this.objectMapper = objectMapper;
  }

  public DevelopmentTaskRunResult run(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    DevelopmentNode node = requireNode(nodeId);
    TaskDefinition definition =
        normalizeDefinition(node, taskType, schemaVersion, content, configJson);
    if (!taskExecutionGateway.supports(definition.taskType())) {
      throw new DevelopmentTaskValidationException(
          "当前未安装 " + definition.taskType() + " Task Runtime，无法运行",
          List.of(
              new TaskValidationIssue(
                  "TASK_RUNTIME_NOT_INSTALLED",
                  "taskType",
                  "Task runtime is not installed: " + definition.taskType())));
    }

    long started = System.nanoTime();
    try {
      TaskVersionSnapshot snapshot = currentDraftSnapshot(node, definition);
      TaskExecution execution =
          taskExecutionGateway.start(
              snapshot,
              TaskExecutionTrigger.MANUAL,
              null,
              Map.of("nodeId", String.valueOf(nodeId)));
      TaskExecution completed = awaitTerminal(snapshot.type(), execution);
      return new DevelopmentTaskRunResult(
          mapStatus(completed),
          completed.errorMessage(),
          elapsedMillis(started),
          completed.output());
    } catch (DevelopmentTaskValidationException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new DevelopmentTaskValidationException(
          safeMessage(exception),
          List.of(
              new TaskValidationIssue(
                  "TASK_RUNTIME_VALIDATION_FAILED",
                  "definition",
                  safeMessage(exception))));
    } catch (Exception exception) {
      String message = safeMessage(exception);
      return new DevelopmentTaskRunResult(
          TaskExecutionStatus.FAILED,
          message,
          elapsedMillis(started),
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

  private DevelopmentNode requireNode(Long nodeId) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    return nodeRepository
        .findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
  }

  private TaskDefinition normalizeDefinition(
      DevelopmentNode node,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("taskType 不能为空");
    }
    String normalizedType = taskType.trim().toUpperCase(Locale.ROOT);
    if (!normalizedType.equals(node.type().trim().toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "任务类型与节点类型不一致：node=" + node.type() + ", definition=" + normalizedType);
    }
    if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion 必须大于 0");
    return new TaskDefinition(
        normalizedType,
        schemaVersion,
        content == null ? "" : content,
        normalizeConfigJson(configJson));
  }

  private String normalizeConfigJson(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("configJson 必须是 JSON Object");
      }
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("configJson 不是合法 JSON", exception);
    }
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
