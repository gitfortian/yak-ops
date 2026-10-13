package io.yak.ops.plugin.task.api.execution;

import java.util.Objects;

/** Immutable identity of one authorized Task Instance. */
public record TaskExecutionIdentity(String workspaceId, String definitionId, int definitionVersion, String instanceId) {

    public TaskExecutionIdentity {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(instanceId, "instanceId");
        if (workspaceId.isBlank() || definitionId.isBlank() || instanceId.isBlank()) {
            throw new IllegalArgumentException("Task execution identities must not be blank");
        }
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("Definition version must be positive");
        }
    }
}
