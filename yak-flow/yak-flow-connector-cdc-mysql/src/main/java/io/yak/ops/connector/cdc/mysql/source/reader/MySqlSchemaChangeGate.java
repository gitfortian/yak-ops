package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mailbox-owned boundary between an emitted DDL request and the next MySQL row event.
 *
 * <p>Never blocks the Source mailbox. The committed Binlog offset is advanced only when the
 * coordinator acknowledgment is consumed on the same mailbox. Checkpoints decline while the
 * boundary is outstanding, so source offset and SchemaManager version cannot diverge.
 */
public final class MySqlSchemaChangeGate {

    private final SourceReaderContext context;
    private CompletableFuture<Long> pending;
    private Runnable advance;

    public MySqlSchemaChangeGate(SourceReaderContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void begin(SourceSchemaChange change, Runnable advanceOffset) {
        if (pending != null) {
            throw new IllegalStateException("A MySQL schema transition is already in progress");
        }
        advance = Objects.requireNonNull(advanceOffset, "advanceOffset");
        CompletionStage<Long> applied = context.sendSourceSchemaChange(Objects.requireNonNull(change, "change"));
        pending =
                Objects.requireNonNull(applied, "schema change acknowledgment").toCompletableFuture();
    }

    /** Returns false until the coordinator has committed the target and released the Source. */
    public boolean consumeAcknowledgment() {
        CompletableFuture<Long> inFlight = pending;
        if (inFlight == null) return true;
        if (!inFlight.isDone()) return false;
        inFlight.join();
        advance.run();
        advance = null;
        pending = null;
        return true;
    }

    public CompletableFuture<Void> availability() {
        CompletableFuture<Long> inFlight = pending;
        return inFlight == null ? null : inFlight.thenApply(ignored -> null);
    }

    /** A completed but not yet consumed ACK still cannot move the checkpointed offset. */
    public void requireCheckpointReady() {
        if (pending != null) {
            throw new IllegalStateException("Cannot checkpoint across an unacknowledged MySQL schema event");
        }
    }
}
