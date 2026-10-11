package io.yak.ops.plugin.task.api;

import io.yak.ops.common.util.JSONUtils;

/** Type-specific parameter interpretation; Task Business owns identity and versions. */
public interface TaskPlugin {
    String type();

    /** Reads the canonical versioned JSON representation. */
    TaskParameters parseParameters(String parametersJson);

    /** Optional user-facing format; non-sync plugins remain JSON-only by default. */
    default TaskParameters parseParameters(String parameters, String format) {
        if (!"JSON".equalsIgnoreCase(format)) {
            throw new TaskPluginException("Unsupported task parameters format");
        }
        return parseParameters(parameters);
    }

    /** Formats a canonical parameter snapshot for user-facing export. */
    default String formatParameters(TaskParameters parameters, String format) {
        if (!"JSON".equalsIgnoreCase(format)) {
            throw new TaskPluginException("Unsupported task parameters format");
        }
        return JSONUtils.toJson(parameters);
    }

    default int parameterSchemaVersion() {
        return 1;
    }

    default TaskParameters normalize(TaskParameters parameters, TaskValidationContext context) {
        return parameters;
    }

    default void validateDefinition(TaskParameters parameters, TaskValidationContext context) {}
}
