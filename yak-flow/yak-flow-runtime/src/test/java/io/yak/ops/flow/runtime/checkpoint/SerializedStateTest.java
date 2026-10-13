package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot.SerializedState;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SerializedStateTest {

    @Test
    void comparesVersionedStateByBytesAndPreservesHashContract() {
        SerializedState state = new SerializedState(2, new byte[] {1, 2, 3});
        SerializedState same = new SerializedState(2, new byte[] {1, 2, 3});
        SerializedState otherVersion = new SerializedState(3, new byte[] {1, 2, 3});
        SerializedState otherBytes = new SerializedState(2, new byte[] {1, 2, 4});

        assertEquals(state, same);
        assertEquals(state.hashCode(), same.hashCode());
        assertNotEquals(state, otherVersion);
        assertNotEquals(state, otherBytes);

        Map<SerializedState, String> indexed = new HashMap<>();
        indexed.put(state, "snapshot");
        assertEquals("snapshot", indexed.get(same));
    }

    @Test
    void copiesInputAndReturnedBytesWithoutExposingPayloadInDiagnostics() {
        byte[] source = {11, 22, 33};
        SerializedState state = new SerializedState(1, source);
        source[0] = 99;
        assertArrayEquals(new byte[] {11, 22, 33}, state.bytes());

        byte[] exposed = state.bytes();
        exposed[1] = 99;
        assertArrayEquals(new byte[] {11, 22, 33}, state.bytes());
        assertEquals(state, new SerializedState(1, new byte[] {11, 22, 33}));

        assertTrue(state.toString().contains("version=1"));
        assertTrue(state.toString().contains("bytes=redacted"));
        assertFalse(state.toString().contains("[11, 22, 33]"));
    }

    @Test
    void rejectsInvalidCheckpointValues() {
        assertThrows(IllegalArgumentException.class, () -> new SerializedState(-1, new byte[] {1}));
        assertThrows(NullPointerException.class, () -> new SerializedState(0, null));
    }
}
