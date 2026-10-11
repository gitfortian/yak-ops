package io.yak.ops.plugin.task.api;

/** Type-specific parameters and validation only; Task Business owns identity and versions. */
public interface TaskPlugin {
    String type();

    TaskParameters parseParameters(String parametersJson);

    default int parameterSchemaVersion() {
        return 1;
    }

    default TaskParameters normalize(TaskParameters parameters, TaskValidationContext context) {
        return parameters;
    }

    default void validateDefinition(TaskParameters parameters, TaskValidationContext context) {}
}
