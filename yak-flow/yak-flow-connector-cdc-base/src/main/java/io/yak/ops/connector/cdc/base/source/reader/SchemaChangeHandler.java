package io.yak.ops.connector.cdc.base.source.reader;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates source schema changes on the Reader mailbox without blocking on the acknowledgment.
 *
 * <p>The Source advances its own offset only after the coordinator acknowledgment is consumed
 * by the mailbox. A checkpoint stays blocked until the offset update succeeds, even when the
 * acknowledgment future has already completed.
 */
public final class SchemaChangeHandler {

    private final SourceReaderContext context;
    private PendingSchemaChange pending;

    public SchemaChangeHandler(SourceReaderContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void submitSchemaChange(SourceSchemaChange change, Runnable advanceOffset) {
        if (pending != null) {
            throw new IllegalStateException("A CDC schema transition is already in progress");
        }
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(advanceOffset, "advanceOffset");
        CompletionStage<Long> acknowledgment =
                Objects.requireNonNull(context.sendSourceSchemaChange(change), "schema change acknowledgment");
        pending = new PendingSchemaChange(acknowledgment.toCompletableFuture(), advanceOffset);
    }

    /**
     * Consumes the acknowledgment on the mailbox and advances its source offset.
     *
     * @return false while the coordinator is still processing the change
     */
    public boolean tryCompleteSchemaChange() {
        PendingSchemaChange change = pending;
        if (change == null) {
            return true;
        }
        if (!change.acknowledgment().isDone()) {
            return false;
        }
        change.acknowledgment().join();
        change.advanceOffset().run();
        pending = null;
        return true;
    }

    /** Returns the pending wake-up future, or null when the reader can poll normally. */
    public CompletableFuture<Void> getAvailableFuture() {
        PendingSchemaChange change = pending;
        return change == null ? null : change.acknowledgment().thenApply(ignored -> null);
    }

    /** A completed ACK still cannot enter a checkpoint until its offset was advanced. */
    public void requireNoPendingSchemaChange() {
        if (pending != null) {
            throw new IllegalStateException("Cannot checkpoint across an unacknowledged CDC schema event");
        }
    }

    private record PendingSchemaChange(CompletableFuture<Long> acknowledgment, Runnable advanceOffset) {}
}
