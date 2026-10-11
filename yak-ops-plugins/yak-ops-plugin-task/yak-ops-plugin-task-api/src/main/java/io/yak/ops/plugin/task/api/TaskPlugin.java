package io.yak.ops.plugin.task.api;

/** Type-specific parameters and validation only; Task Business owns identity and versions. */
public interface TaskPlugin {
    /** Returns the stable canonical type used to select this plugin. */
    String type();

    /**
     * Parses plugin parameters without resolving credentials or accessing persistent state.
     *
     * @param parametersJson persisted plugin-specific JSON
     * @return typed parameters to normalize and validate
     */
    TaskParameters parseParameters(String parametersJson);

    /** Returns the version used to interpret this plugin's persisted parameter schema. */
    default int parameterSchemaVersion() {
        return 1;
    }

    /**
     * Normalizes parameters using only the host's read-only, Workspace-scoped metadata.
     *
     * @return normalized parameters suitable for versioned persistence
     */
    default TaskParameters normalize(TaskParameters parameters, TaskValidationContext context) {
        return parameters;
    }

    /** Rejects a definition violating this plugin's supported schema or datasource boundaries. */
    default void validateDefinition(TaskParameters parameters, TaskValidationContext context) {}
}
