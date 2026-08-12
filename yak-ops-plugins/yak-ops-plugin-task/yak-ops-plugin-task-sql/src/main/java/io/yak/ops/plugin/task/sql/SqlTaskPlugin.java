package io.yak.ops.plugin.task.sql;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage-2 SQL task plugin skeleton.
 *
 * <p>This plugin proves SPI discovery and definition validation only. Datasource-backed SQL
 * execution is deliberately deferred to the SQL execution stage.
 */
public final class SqlTaskPlugin implements TaskPlugin {

  public static final String TYPE = "SQL";
  private static final int SCHEMA_VERSION = 1;

  private static final TaskPluginDescriptor DESCRIPTOR =
      new TaskPluginDescriptor(
          TYPE,
          "SQL",
          "SQL task definition",
          "1.0.0",
          SCHEMA_VERSION,
          false,
          false);

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
              "SQL plugin only accepts taskType=SQL"));
    }
    if (definition.schemaVersion() != SCHEMA_VERSION) {
      issues.add(
          new TaskValidationIssue(
              "UNSUPPORTED_SCHEMA_VERSION",
              "schemaVersion",
              "SQL plugin currently supports schemaVersion=1"));
    }
    if (definition.content() == null || definition.content().isBlank()) {
      issues.add(
          new TaskValidationIssue(
              "SQL_CONTENT_REQUIRED",
              "content",
              "SQL content must not be blank"));
    }

    return issues.isEmpty() ? TaskValidationResult.ok() : TaskValidationResult.of(issues);
  }
}
