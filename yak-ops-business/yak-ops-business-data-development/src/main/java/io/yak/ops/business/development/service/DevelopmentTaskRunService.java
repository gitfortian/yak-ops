package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRunResult;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Executes the current editor definition through the platform TaskPlugin contract. */
@Service
public class DevelopmentTaskRunService {

  private final DevelopmentNodeRepository nodeRepository;
  private final TaskPluginRegistry pluginRegistry;
  private final ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider;
  private final ObjectMapper objectMapper;

  public DevelopmentTaskRunService(
      DevelopmentNodeRepository nodeRepository,
      TaskPluginRegistry pluginRegistry,
      ObjectProvider<DataSourceExecutionProvider> dataSourceExecutionProvider,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.pluginRegistry = pluginRegistry;
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
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
    TaskPlugin plugin =
        pluginRegistry
            .find(definition.taskType())
            .orElseThrow(
                () ->
                    new DevelopmentTaskValidationException(
                        "当前未安装 " + definition.taskType() + " Task Plugin，无法运行",
                        List.of(
                            new TaskValidationIssue(
                                "TASK_PLUGIN_NOT_INSTALLED",
                                "taskType",
                                "Task plugin is not installed: " + definition.taskType()))));
    if (!plugin.descriptor().executable()) {
      throw new DevelopmentTaskValidationException(
          definition.taskType() + " Task Plugin 暂不支持执行",
          List.of(
              new TaskValidationIssue(
                  "TASK_PLUGIN_NOT_EXECUTABLE",
                  "taskType",
                  "Task plugin is not executable: " + definition.taskType())));
    }
    validate(plugin, definition);

    long started = System.nanoTime();
    try {
      TaskExecutor executor =
          plugin.createExecutor(
              definition,
              new ManualExecutionContext(
                  nodeId,
                  dataSourceExecutionProvider.getIfAvailable()));
      TaskExecutionResult result = executor.execute();
      return new DevelopmentTaskRunResult(
          result.status(),
          result.message(),
          elapsedMillis(started),
          result.output());
    } catch (DevelopmentTaskValidationException exception) {
      throw exception;
    } catch (Exception exception) {
      String message = safeMessage(exception);
      return new DevelopmentTaskRunResult(
          TaskExecutionStatus.FAILED,
          message,
          elapsedMillis(started),
          Map.of());
    }
  }

  private void validate(TaskPlugin plugin, TaskDefinition definition) {
    TaskValidationResult validation = plugin.validate(definition);
    if (validation.valid()) return;
    String summary =
        validation.issues().stream()
            .map(TaskValidationIssue::message)
            .limit(3)
            .reduce((left, right) -> left + "；" + right)
            .orElse("任务定义校验失败");
    throw new DevelopmentTaskValidationException(summary, validation.issues());
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

  private record ManualExecutionContext(
      Long nodeId,
      DataSourceExecutionProvider dataSourceExecutionProvider)
      implements TaskExecutionContext {

    @Override
    public TaskExecutionTrigger trigger() {
      return TaskExecutionTrigger.MANUAL;
    }

    @Override
    public Map<String, Object> parameters() {
      return Map.of("nodeId", String.valueOf(nodeId));
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
