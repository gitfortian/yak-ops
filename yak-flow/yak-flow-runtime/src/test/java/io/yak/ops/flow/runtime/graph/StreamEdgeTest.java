package io.yak.ops.flow.runtime.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.operators.KeySelector;
import org.junit.jupiter.api.Test;

class StreamEdgeTest {

    @Test
    void preservesConstructorAndSelectorContracts() throws Exception {
        StreamEdge forward = new StreamEdge(1, 2);
        assertEquals(1, forward.sourceId());
        assertEquals(2, forward.targetId());
        assertEquals(StreamPartitioning.FORWARD, forward.partitioning());
        assertNull(forward.keySelector());

        KeySelector<String> selector = value -> value.substring(0, 1);
        StreamEdge keyed = StreamEdge.keyed(1, 2, selector);
        assertEquals(StreamPartitioning.KEYED, keyed.partitioning());
        assertSame(selector, keyed.keySelector());
        assertEquals("a", ((KeySelector<String>) keyed.keySelector()).getKey("alpha"));
    }

    @Test
    void edgesUseGraphOwnedIdentityInsteadOfFunctionObjectEquality() {
        StreamEdge first = new StreamEdge(1, 2, StreamPartitioning.REBALANCE);
        StreamEdge second = new StreamEdge(1, 2, StreamPartitioning.REBALANCE);

        assertNotEquals(first, second);
        assertFalse(first.getClass().isRecord());
        assertTrue(first.toString().contains("partitioning=REBALANCE"));
        assertFalse(first.toString().contains("keySelector"));
    }

    @Test
    void invalidEdgeDefinitionsRemainRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 1));
        assertThrows(NullPointerException.class, () -> new StreamEdge(1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 2, StreamPartitioning.KEYED));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StreamEdge(1, 2, StreamPartitioning.FORWARD, (KeySelector<String>) value -> value));
    }
}
