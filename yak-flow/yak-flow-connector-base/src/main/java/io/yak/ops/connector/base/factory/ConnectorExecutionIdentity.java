package io.yak.ops.connector.base.factory;

import java.util.Objects;

/** Stable non-secret identity available to Connector admission and construction. */
public record ConnectorExecutionIdentity(String workspaceId, String definitionId, String instanceId) {

    public ConnectorExecutionIdentity {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(instanceId, "instanceId");
    }
}
