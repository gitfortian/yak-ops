package io.yak.ops.flow.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.BasicType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.VarCharType;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.FileCheckpointStore;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class SchemaManagerTest {

    @TempDir
    Path directory;

    private static final TableId SOURCE = new TableId("source", "sales", "orders");
    private static final TableId TARGET = new TableId("target", "warehouse", "orders");

    @Test
    void commitsMonotonicVersionAndRecognizesReplay() {
        Schema before = schema(false);
        Schema after = schema(true);
        SchemaChangeRequest request = request("binlog:0001:100", before, after, "note");

        SchemaManager manager = new SchemaManager();
        SchemaManager.Change change = manager.begin(request);
        assertFalse(change.duplicate());
        assertEquals(1, change.version());
        assertEquals(1, manager.complete(request));
        assertEquals(1, manager.currentVersion(TARGET));

        SchemaManager.Change replay = manager.begin(request);
        assertTrue(replay.duplicate());
        assertEquals(1, replay.version());
        assertEquals(1, manager.snapshot().tables().getFirst().version());
    }

    @Test
    void rejectsStaleSchemaAndReusedEventId() {
        Schema before = schema(false);
        Schema after = schema(true);
        SchemaChangeRequest first = request("binlog:0001:100", before, after, "note");

        SchemaManager manager = new SchemaManager();
        manager.begin(first);
        manager.complete(first);

        Schema third = schema(true, "note", "region");
        SchemaChangeRequest stale = request("binlog:0001:101", before, third, after, third, "region");
        assertThrows(IllegalStateException.class, () -> manager.begin(stale));

        SchemaChangeRequest reusedId = request("binlog:0001:100", after, third, after, third, "region");
        assertThrows(IllegalStateException.class, () -> manager.begin(reusedId));
    }

    @Test
    void schemaManagerSnapshotRoundTripsWithoutJavaSerialization() throws Exception {
        Schema before = schema(false);
        Schema after = schema(true);
        SchemaChangeRequest request = request("binlog:0001:100", before, after, "note");
        SchemaManager manager = new SchemaManager();
        manager.begin(request);
        manager.complete(request);

        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        byte[] bytes = serializer.serialize(manager.snapshot());
        SchemaManagerSnapshot restoredSnapshot = serializer.deserialize(serializer.getVersion(), bytes);
        SchemaManager restored = new SchemaManager();
        restored.restore(restoredSnapshot);

        assertEquals(after, restored.state(TARGET).orElseThrow().sourceSchema());
        assertEquals(after, restored.state(TARGET).orElseThrow().targetSchema());
        assertTrue(restored.begin(request).duplicate());
        assertEquals(1, restored.currentVersion(TARGET));
    }

    @Test
    void checkpointFormatPersistsSchemaStateAlongsideSourceOffsets() throws Exception {
        Schema before = schema(false);
        Schema after = schema(true);
        SchemaChangeRequest request = request("binlog:0001:100", before, after, "note");
        SchemaManager manager = new SchemaManager();
        manager.begin(request);
        manager.complete(request);
        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        CheckpointSnapshot.SerializedState schemaState =
                new CheckpointSnapshot.SerializedState(serializer.getVersion(), serializer.serialize(manager.snapshot()));
        CheckpointSnapshot checkpoint = new CheckpointSnapshot(
                1,
                "schema-test-graph",
                new CheckpointSnapshot.SerializedState(0, new byte[] {1}),
                Map.of(),
                Map.of(),
                System.currentTimeMillis(),
                Map.of(),
                Set.of(),
                Set.of(),
                schemaState);

        try (FileCheckpointStore store = new FileCheckpointStore(directory)) {
            store.save(checkpoint);
            CheckpointSnapshot restored = store.loadLatest("schema-test-graph").orElseThrow();
            assertEquals(schemaState, restored.schemaManagerState());
        }
    }

    @Test
    void rejectsTargetEventsThatDoNotProduceDeclaredSchema() {
        Schema before = schema(false);
        Schema after = schema(true);
        Schema mismatched = schema(true, "other");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeRequest(
                        "binlog:0001:100",
                        SOURCE,
                        TARGET,
                        before,
                        after,
                        before,
                        mismatched,
                        java.util.List.of(new AddColumnEvent(TARGET, List.of(AddColumnEvent.last(new PhysicalColumn("note", new VarCharType(64))))))));
    }

    private static SchemaChangeRequest request(String eventId, Schema before, Schema after, String addedColumn) {
        return request(eventId, before, after, before, after, addedColumn);
    }

    private static SchemaChangeRequest request(
            String eventId,
            Schema sourceBefore,
            Schema sourceAfter,
            Schema targetBefore,
            Schema targetAfter,
            String addedColumn) {
        return new SchemaChangeRequest(
                eventId,
                SOURCE,
                TARGET,
                sourceBefore,
                sourceAfter,
                targetBefore,
                targetAfter,
                java.util.List.of(new AddColumnEvent(TARGET, List.of(AddColumnEvent.last(new PhysicalColumn(addedColumn, new VarCharType(64)))))));
    }

    private static Schema schema(boolean includeNote) {
        return schema(includeNote, "note");
    }

    private static Schema schema(boolean includeNote, String... extraColumns) {
        java.util.List<PhysicalColumn> columns =
                new java.util.ArrayList<>(java.util.List.of(new PhysicalColumn("id", new BasicType(false, LogicalTypeRoot.BIGINT))));
        if (includeNote) {
            for (String name : extraColumns) {
                columns.add(new PhysicalColumn(name, new VarCharType(64)));
            }
        }
        return new Schema(columns, java.util.List.of("id"));
    }
}
