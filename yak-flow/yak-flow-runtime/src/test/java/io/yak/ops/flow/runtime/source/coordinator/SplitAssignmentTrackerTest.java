package io.yak.ops.flow.runtime.source.coordinator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests Flink's Source Coordinator split assignment checkpoint contract. */
class SplitAssignmentTrackerTest {

    @Test
    void testRecordIncrementalSplitAssignment() {
        SplitAssignmentTracker<TestSplit> tracker = new SplitAssignmentTracker<>();
        tracker.recordSplitAssignment(assignment("first", "second"));
        tracker.recordSplitAssignment(assignment("third"));
        assertEquals(List.of(new TestSplit("first"), new TestSplit("second"), new TestSplit("third")),
                List.copyOf(tracker.uncheckpointedAssignments().get(0)));
    }

    @Test
    void testOnCheckpoint() throws Exception {
        SplitAssignmentTracker<TestSplit> tracker = new SplitAssignmentTracker<>();
        tracker.recordSplitAssignment(assignment("first"));
        tracker.onCheckpoint(100);
        assertTrue(tracker.uncheckpointedAssignments().isEmpty());
        assertEquals(List.of(new TestSplit("first")),
                List.copyOf(tracker.assignmentsByCheckpointId(100).get(0)));
        tracker.recordSplitAssignment(assignment("second"));
        tracker.onCheckpoint(101);
        tracker.onCheckpointComplete(100);
        assertFalse(tracker.assignmentsByCheckpointId().containsKey(100L));
        assertEquals(List.of(new TestSplit("second")),
                List.copyOf(tracker.assignmentsByCheckpointId(101).get(0)));
    }

    @Test
    void testGetAndRemoveUncheckpointedAssignment() throws Exception {
        SplitAssignmentTracker<TestSplit> tracker = new SplitAssignmentTracker<>();
        tracker.recordSplitAssignment(assignment("before"));
        tracker.onCheckpoint(100);
        tracker.recordSplitAssignment(assignment("after"));
        tracker.onCheckpoint(101);
        tracker.recordSplitAssignment(assignment("pending"));
        assertEquals(List.of(new TestSplit("after"), new TestSplit("pending")),
                tracker.getAndRemoveUncheckpointedAssignment(0, 100));
        assertTrue(tracker.uncheckpointedAssignments().isEmpty());
        assertTrue(tracker.assignmentsByCheckpointId(101).isEmpty());
        assertEquals(List.of(new TestSplit("before")),
                List.copyOf(tracker.assignmentsByCheckpointId(100).get(0)));
    }

    @Test
    void testAssignmentSerializationMatchesFlinkLayout() throws Exception {
        TestSplitSerializer serializer = new TestSplitSerializer();
        SplitAssignmentTracker<TestSplit> tracker = new SplitAssignmentTracker<>();
        tracker.recordSplitAssignment(assignment("a", "b"));
        byte[] state = tracker.snapshotState(serializer);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(0);
            out.writeInt(2);
            for (String id : List.of("a", "b")) {
                byte[] encoded = id.getBytes(StandardCharsets.UTF_8);
                out.writeInt(encoded.length);
                out.write(encoded);
            }
        }
        assertArrayEquals(bytes.toByteArray(), state);
        SplitAssignmentTracker<TestSplit> recovered = new SplitAssignmentTracker<>();
        recovered.restoreState(serializer, state);
        assertEquals(List.of(new TestSplit("a"), new TestSplit("b")),
                List.copyOf(recovered.uncheckpointedAssignments().get(0)));
        assertThrows(IOException.class, () -> recovered.restoreState(serializer, new byte[] {0, 1}));
    }

    private static SplitsAssignment<TestSplit> assignment(String... ids) {
        return new SplitsAssignment<>(Map.of(0,
                java.util.Arrays.stream(ids).map(TestSplit::new).toList()));
    }

    private static final class TestSplit implements SourceSplit {
        private final String id;

        private TestSplit(String id) {
            this.id = id;
        }

        @Override
        public String splitId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestSplit that && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestSplitSerializer implements SimpleVersionedSerializer<TestSplit> {
        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public byte[] serialize(TestSplit split) {
            return split.splitId().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public TestSplit deserialize(int version, byte[] serialized) throws IOException {
            if (version != 1) {
                throw new IOException("Invalid split version");
            }
            return new TestSplit(new String(serialized, StandardCharsets.UTF_8));
        }
    }
}
