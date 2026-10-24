package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handles a MySQL source schema change while the Reader mailbox is waiting for coordinator
 * acknowledgment.
 *
 * <p>Submitting a DDL never blocks the mailbox. The Source advances its Binlog offset only
 * when the completed acknowledgment is consumed by the mailbox. Checkpoints remain blocked
 * until that offset update succeeds, including when the coordinator future has completed.
 */
public final class MySqlSchemaChangeHandler {

    private final SourceReaderContext context;
    private PendingSchemaChange pending;

    public MySqlSchemaChangeHandler(SourceReaderContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void submitSchemaChange(SourceSchemaChange change, Runnable advanceOffset) {
        if (pending != null) {
            throw new IllegalStateException("A MySQL schema transition is already in progress");
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
            throw new IllegalStateException("Cannot checkpoint across an unacknowledged MySQL schema event");
        }
    }

    private record PendingSchemaChange(CompletableFuture<Long> acknowledgment, Runnable advanceOffset) {}
}
