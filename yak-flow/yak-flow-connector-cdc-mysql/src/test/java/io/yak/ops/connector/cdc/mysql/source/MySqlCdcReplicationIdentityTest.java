package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.factory.ConnectorExecutionIdentity;
import org.junit.jupiter.api.Test;

/** Verifies stable CDC replication identity and collision detection through resource admission. */
class MySqlCdcReplicationIdentityTest {

    @Test
    void keepsServerIdAndTopicPrefixStableForOneInstanceAcrossAttempts() {
        ConnectorExecutionIdentity first = identity("instance-123", "attempt-1");
        ConnectorExecutionIdentity next = identity("instance-123", "attempt-2");

        assertEquals(
                MySqlCdcReplicationIdentity.serverId(first), MySqlCdcReplicationIdentity.serverId(next));
        assertEquals(
                MySqlCdcReplicationIdentity.topicPrefix(first), MySqlCdcReplicationIdentity.topicPrefix(next));
        assertEquals(
                MySqlCdcReplicationIdentity.exclusiveResource(first),
                MySqlCdcReplicationIdentity.exclusiveResource(next));
        assertTrue(MySqlCdcReplicationIdentity.serverId(first) > 0);
    }

    @Test
    void exposesHashCollisionsAsTheSameExclusiveAdmissionClaim() {
        ConnectorExecutionIdentity first = identity("Aa", "attempt-1");
        ConnectorExecutionIdentity second = identity("BB", "attempt-1");
        // Distinct Java Strings with the same hashCode produce the same replication client ID.
        assertNotEquals(first.instanceId(), second.instanceId());
        assertEquals(
                MySqlCdcReplicationIdentity.serverId(first), MySqlCdcReplicationIdentity.serverId(second));
        assertEquals(
                MySqlCdcReplicationIdentity.exclusiveResource(first),
                MySqlCdcReplicationIdentity.exclusiveResource(second));
        assertNotEquals(
                MySqlCdcReplicationIdentity.topicPrefix(first), MySqlCdcReplicationIdentity.topicPrefix(second));
    }

    private static ConnectorExecutionIdentity identity(String instance, String attempt) {
        return new ConnectorExecutionIdentity("ws", "definition", instance);
    }
}
