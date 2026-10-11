package io.yak.ops.plugin.task.api;

/**
 * Defines plugin-specific Task parameters validated without Workspace or Datasource access.
 */
public interface TaskParameters {

    /** Validates local parameter structure and semantics without resolving credentials or querying a database. */
    void validate();
}
