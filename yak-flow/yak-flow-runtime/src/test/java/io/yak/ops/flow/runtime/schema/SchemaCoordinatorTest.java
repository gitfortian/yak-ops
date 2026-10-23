package io.yak.ops.flow.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.BasicType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.VarCharType;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SchemaCoordinatorTest {

    private static final TableId SOURCE = new TableId("source", "sales", "orders");
    private static final TableId TARGET = new TableId("target", "warehouse", "orders");

    @Test
    void drainsAndFlushesEveryWriterBeforeApplyingDdlThenCommitsVersion() throws Exception {
        List<String> order = new ArrayList<>();
        FakeWriter first = new FakeWriter("first", order);
        FakeWriter second = new FakeWriter("second", order);
        FakeSource source = new FakeSource(0, order);
        RecordingApplier applier = new RecordingApplier(order, false);
        SchemaManager manager = new SchemaManager();

        try (SchemaCoordinator coordinator = new SchemaCoordinator(
                manager, applier, List.of(source), List.of(first, second), List.of(first, second), ignored -> {})) {
            assertEquals(1, coordinator.applySchemaChange(request(), 0).toCompletableFuture().get());
            assertTrue(order.indexOf("flush-first") < order.indexOf("ddl"));
            assertTrue(order.indexOf("flush-second") < order.indexOf("ddl"));
            assertTrue(order.indexOf("commit-first") > order.indexOf("ddl"));
            assertTrue(order.indexOf("commit-second") > order.indexOf("ddl"));
            assertTrue(order.indexOf("resume-source-0") > order.indexOf("commit-second"));
            assertEquals(1, manager.currentVersion(TARGET));

            int count = order.size();
            assertEquals(1, coordinator.applySchemaChange(request(), 0).toCompletableFuture().get());
            assertEquals(count, order.size(), "a replay must not flush Writers or reapply DDL");
        }
    }

    @Test
    void checkpointSnapshotContainsOnlyCommittedSchemaAndCanBeRestored() throws Exception {
        List<String> order = new ArrayList<>();
        SchemaManager manager = new SchemaManager();
        FakeWriter sink = new FakeWriter("sink", order);
        try (SchemaCoordinator coordinator = new SchemaCoordinator(
                manager,
                new RecordingApplier(order, false),
                List.of(new FakeSource(0, order)),
                List.of(sink),
                List.of(sink),
                ignored -> {})) {
            // The coordinator snapshots only after its checkpoint fence has drained active changes.
            coordinator.pauseForCheckpoint().get();
            CheckpointSnapshot.SerializedState state = coordinator.snapshotState();
            SchemaManager restored = SchemaCoordinator.restoreManager(state);
            assertEquals(0, restored.currentVersion(TARGET));
            coordinator.resumeAfterCheckpoint().get();
        }
    }

    @Test
    void failedDdlRecoversFromThePriorCheckpointAndReplaysTheSameEvent() throws Exception {
        SchemaManager initial = new SchemaManager();
        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        CheckpointSnapshot.SerializedState checkpoint =
                new CheckpointSnapshot.SerializedState(serializer.getVersion(), serializer.serialize(initial.snapshot()));
        List<String> firstOrder = new ArrayList<>();
        AtomicBoolean failure = new AtomicBoolean();
        RecordingApplier failingApplier = new RecordingApplier(firstOrder, true);
        SchemaCoordinator failed = new SchemaCoordinator(
                initial,
                failingApplier,
                List.of(new FakeSource(0, firstOrder)),
                List.of(new FakeWriter("sink", firstOrder)),
                List.of(new FakeWriter("sink", firstOrder)),
                failureValue -> failure.set(true));
        assertThrows(
                ExecutionException.class,
                () -> failed.applySchemaChange(request(), 0).toCompletableFuture().get());
        assertTrue(failure.get());
        failed.close();

        SchemaManager recovered = SchemaCoordinator.restoreManager(checkpoint);
        List<String> retryOrder = new ArrayList<>();
        try (SchemaCoordinator coordinator = new SchemaCoordinator(
                recovered,
                new RecordingApplier(retryOrder, false),
                List.of(new FakeSource(0, retryOrder)),
                List.of(new FakeWriter("sink", retryOrder)),
                List.of(new FakeWriter("sink", retryOrder)),
                ignored -> {})) {
            assertEquals(1, coordinator.applySchemaChange(request(), 0).toCompletableFuture().get());
            assertEquals(1, recovered.currentVersion(TARGET));
        }
    }

    private static SchemaChangeRequest request() {
        Schema before = new Schema(
                List.of(new PhysicalColumn("id", new BasicType(false, LogicalTypeRoot.BIGINT))),
                List.of("id"));
        Schema after = new Schema(
                List.of(
                        new PhysicalColumn("id", new BasicType(false, LogicalTypeRoot.BIGINT)),
                        new PhysicalColumn("note", new VarCharType(64))),
                List.of("id"));
        return new SchemaChangeRequest(
                "mysql-bin.000001:100:orders",
                SOURCE,
                TARGET,
                before,
                after,
                before,
                after,
                List.of(new AddColumnEvent(TARGET, List.of(AddColumnEvent.last(new PhysicalColumn("note", new VarCharType(64)))))));
    }

    private static final class FakeSource implements SchemaSourceParticipant {
        private final int subtask;
        private final List<String> order;

        private FakeSource(int subtask, List<String> order) {
            this.subtask = subtask;
            this.order = order;
        }

        @Override
        public int subtaskIndex() {
            return subtask;
        }

        @Override
        public CompletableFuture<Void> pauseForSchemaChange() {
            order.add("pause-source-" + subtask);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> resumeAfterSchemaChange() {
            order.add("resume-source-" + subtask);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeWriter implements SchemaChangeParticipant {
        private final String name;
        private final List<String> order;

        private FakeWriter(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public CompletableFuture<Void> validateSchemaChange(SchemaChangeRequest request) {
            order.add("validate-" + name);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> drainForSchemaChange() {
            order.add("drain-" + name);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> prepareSchemaChange(SchemaChangeRequest request) {
            order.add("flush-" + name);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> commitSchemaChange(SchemaChangeRequest request, long schemaVersion) {
            order.add("commit-" + name);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingApplier implements MetadataApplier {
        private final List<String> order;
        private final boolean failFirstApply;
        private final AtomicBoolean failed = new AtomicBoolean();

        private RecordingApplier(List<String> order, boolean failFirstApply) {
            this.order = order;
            this.failFirstApply = failFirstApply;
        }

        @Override
        public void applySchemaChange(SchemaChangeEvent schemaChangeEvent) {
            order.add("ddl");
            if (failFirstApply && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("simulated DDL failure");
            }
        }

        @Override
        public MetadataApplier setAcceptedSchemaEvolutionTypes(Set<SchemaChangeEventType> types) {
            return this;
        }

        @Override
        public Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
            return Set.of(SchemaChangeEventType.ADD_COLUMN, SchemaChangeEventType.ALTER_COLUMN_TYPE);
        }
    }
}
