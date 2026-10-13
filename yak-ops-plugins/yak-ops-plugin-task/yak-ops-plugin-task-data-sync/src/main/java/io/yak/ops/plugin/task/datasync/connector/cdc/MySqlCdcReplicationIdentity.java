package io.yak.ops.plugin.task.datasync.connector.cdc;

import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.util.Objects;

/**
 * Stable replication identity for all YakFlow attempts belonging to a Task Instance.
 *
 * <p>The exclusive resource claim prevents colliding replication server IDs among active
 * DATA_SYNC instances admitted within the same Workspace. External MySQL clients and separate
 * Workspace claims remain outside the Task platform's admission scope.
 */
public final class MySqlCdcReplicationIdentity {

    private MySqlCdcReplicationIdentity() {}

    /**
     * Derives the replication client server ID from a stable Task Instance identifier.
     *
     * @param identity authorized Task execution identity
     * @return positive MySQL replication client ID
     */
    public static int serverId(TaskExecutionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return Math.floorMod(identity.instanceId().hashCode(), 2_000_000_000) + 1;
    }

    /**
     * Returns the stable Debezium topic namespace for one Instance.
     *
     * @param identity authorized Task execution identity
     * @return topic namespace shared by all attempts of the same Instance
     */
    public static String topicPrefix(TaskExecutionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return "yakflow_" + identity.instanceId();
    }

    /**
     * Reserves the replication client ID through the Task platform's durable admission.
     *
     * @param identity authorized Task execution identity
     * @return Workspace-exclusive replication identity claim
     */
    public static ExecutionResource exclusiveResource(TaskExecutionIdentity identity) {
        return new ExecutionResource("MYSQL_CDC_SERVER_ID", Integer.toString(serverId(identity)));
    }
}
