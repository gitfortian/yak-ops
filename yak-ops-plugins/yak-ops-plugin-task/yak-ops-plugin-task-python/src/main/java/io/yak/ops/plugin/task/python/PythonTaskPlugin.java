package io.yak.ops.plugin.task.python;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Python script task plugin. */
public final class PythonTaskPlugin implements TaskPlugin {

  public static final String TYPE = "PYTHON";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "Python",
          "Execute Python scripts via PYTHON_HOME or configurable interpreter, with arguments and environment",
          "1.1.0",
          SCHEMA_VERSION,
          true,
          true);

  @Override
  public TaskPluginDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public TaskValidationResult validate(TaskDefinition definition) {
    if (definition == null) {
      return TaskValidationResult.invalid(
          new TaskValidationIssue(
              "TASK_DEFINITION_REQUIRED",
              null,
              "Task definition is required"));
    }

    List<TaskValidationIssue> issues = new ArrayList<>();
    if (!TYPE.equalsIgnoreCase(definition.taskType())) {
      issues.add(
          new TaskValidationIssue(
              "TASK_TYPE_MISMATCH",
              "taskType",
              "Python plugin only accepts taskType=PYTHON"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "Python plugin currently supports schemaVersion=1"));
    }
    if (definition.content() == null || definition.content().isBlank()) {
      issues.add(
          new TaskValidationIssue(
              "PYTHON_CONTENT_REQUIRED",
              "content",
              "Python script content must not be blank"));
    }

    try {
      PythonTaskConfig.parse(definition.configJson());
    } catch (IllegalArgumentException exception) {
      issues.add(
          new TaskValidationIssue(
              "PYTHON_CONFIG_INVALID",
              "configJson",
              exception.getMessage() == null ? "Python config is invalid" : exception.getMessage()));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }

  @Override
  public TaskExecutor createExecutor(
      TaskDefinition definition,
      TaskExecutionContext context) {
    TaskValidationResult validation = validate(definition);
    if (!validation.valid()) {
      String summary =
          validation.issues().stream()
              .map(TaskValidationIssue::message)
              .limit(3)
              .reduce((left, right) -> left + "; " + right)
              .orElse("Python task validation failed");
      throw new IllegalArgumentException(summary);
    }
    Map<String, String> globalEnv = context.globalEnvVars();
    PythonTaskConfig config = PythonTaskConfig.parse(definition.configJson(), globalEnv);
    return new PythonTaskExecutor(definition, config, globalEnv);
  }
}
