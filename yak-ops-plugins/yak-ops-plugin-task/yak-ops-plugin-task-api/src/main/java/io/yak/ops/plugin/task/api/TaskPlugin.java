package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.validation.TaskValidationContext;

/**
 * Defines the parameter contract for one registered Task type.
 *
 * <p>Parsing checks the local parameter structure without accessing Datasource. Validation
 * checks authorized resources without mutating the submitted parameters. Plugins do not
 * persist definitions, construct YakFlow jobs, or own execution state.
 */
public interface TaskPlugin {

    /** Stable plugin identifier; aliases are not inferred from case or punctuation. */
    String type();

    /** Schema version used for newly persisted immutable definition parameters. */
    int parameterSchemaVersion();

    /**
     * Parses JSON parameters into a typed definition.
     *
     * <p>The result must have passed plugin-owned local structural validation. This method
     * must not access Workspace resources or evaluate system properties.
     *
     * @throws TaskPluginException for invalid JSON or plugin parameters
     */
    TaskParameters parse(String content);

    /**
     * Checks external resource constraints once for a locally valid definition.
     *
     * <p>The trusted context resolves Datasource metadata within the current Workspace.
     * Implementations must not silently replace an explicitly selected database or schema.
     */
    void validate(TaskParameters parameters, TaskValidationContext context);
}
