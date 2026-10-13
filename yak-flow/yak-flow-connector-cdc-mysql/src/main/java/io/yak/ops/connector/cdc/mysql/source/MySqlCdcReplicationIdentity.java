package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.factory.ConnectorExecutionIdentity;
import io.yak.ops.connector.base.factory.ConnectorResourceClaim;
import java.util.Objects;

/**
 * Stable replication identity for attempts of the same execution instance.
 *
 * <p>The exclusive claim is scoped by the calling execution platform.
 */
public final class MySqlCdcReplicationIdentity {

    private MySqlCdcReplicationIdentity() {}

    /** Returns the stable positive replication server ID for one instance. */
    public static int serverId(ConnectorExecutionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return Math.floorMod(identity.instanceId().hashCode(), 2_000_000_000) + 1;
    }

    /** Returns the stable Debezium topic namespace for one instance. */
    public static String topicPrefix(ConnectorExecutionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return "yakflow_" + identity.instanceId();
    }

    /** Declares the replication server-ID resource for atomic admission. */
    public static ConnectorResourceClaim exclusiveResource(ConnectorExecutionIdentity identity) {
        return new ConnectorResourceClaim("MYSQL_CDC_SERVER_ID", Integer.toString(serverId(identity)));
    }
}
