package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalTypes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MySqlSchemaChangeHandlerTest {

    @Test
    void failedAcknowledgmentKeepsOffsetAndCheckpointBlocked() {
        CompletableFuture<Long> acknowledgment = new CompletableFuture<>();
        MySqlSchemaChangeHandler handler = new MySqlSchemaChangeHandler(context(() -> acknowledgment));
        AtomicInteger advanced = new AtomicInteger();

        handler.submitSchemaChange(change(), advanced::incrementAndGet);
        assertFalse(handler.tryCompleteSchemaChange());
        acknowledgment.completeExceptionally(new IllegalStateException("Schema change rejected"));

        assertThrows(CompletionException.class, handler::tryCompleteSchemaChange);
        assertEquals(0, advanced.get());
        assertThrows(IllegalStateException.class, handler::requireNoPendingSchemaChange);
        assertThrows(IllegalStateException.class, () -> handler.submitSchemaChange(change(), () -> {}));
    }

    @Test
    void transportFailureDoesNotLeaveAStalePendingChange() {
        AtomicInteger attempts = new AtomicInteger();
        MySqlSchemaChangeHandler handler = new MySqlSchemaChangeHandler(context(() -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("Schema coordinator unavailable");
            }
            return CompletableFuture.completedFuture(2L);
        }));
        AtomicInteger advanced = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> handler.submitSchemaChange(change(), () -> {}));
        handler.requireNoPendingSchemaChange();
        assertNull(handler.getAvailableFuture());

        handler.submitSchemaChange(change(), advanced::incrementAndGet);
        assertTrue(handler.tryCompleteSchemaChange());
        assertEquals(1, advanced.get());
        handler.requireNoPendingSchemaChange();
    }

    @Test
    void failedOffsetUpdateCannotReleaseTheCheckpoint() {
        MySqlSchemaChangeHandler handler = new MySqlSchemaChangeHandler(
                context(() -> CompletableFuture.completedFuture(3L)));
        handler.submitSchemaChange(change(), () -> {
            throw new IllegalStateException("Offset could not be committed");
        });

        assertThrows(IllegalStateException.class, handler::tryCompleteSchemaChange);
        assertThrows(IllegalStateException.class, handler::requireNoPendingSchemaChange);
    }

    private static SourceSchemaChange change() {
        Schema before = new Schema(
                List.of(new PhysicalColumn("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        Schema after = new Schema(
                List.of(
                        before.columns().getFirst(),
                        new PhysicalColumn("note", LogicalTypes.varchar(64))),
                List.of("id"));
        return new SourceSchemaChange("ddl-position-100", new TableId("shop", null, "orders"), before, after);
    }

    private static SourceReaderContext context(Supplier<CompletionStage<Long>> response) {
        return new SourceReaderContext() {
            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public int getIndexOfSubtask() {
                return 0;
            }

            @Override
            public int currentParallelism() {
                return 1;
            }

            @Override
            public void sendSplitRequest() {}

            @Override
            public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                return response.get();
            }
        };
    }
}
