package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Shared mailbox lifecycle for MySQL Binlog-only and Hybrid readers.
 *
 * <p>Both modes block emission and checkpoint capture while a schema change is pending.
 * Their mode-specific split state and checkpoint payloads remain owned by the concrete reader.
 */
abstract class MySqlSourceReaderBase<E, SplitT extends MySqlSplit, StateT>
        extends SingleThreadMultiplexSourceReaderBase<E, TableRecord, SplitT, StateT> {

    protected final SchemaChangeHandler schemaChanges;
    protected final TableMetrics metrics;
    protected final long generation;

    protected MySqlSourceReaderBase(
            Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory,
            RecordEmitter<E, TableRecord, StateT> emitter,
            SourceReaderContext context,
            SchemaChangeHandler schemaChanges,
            TableMetrics metrics) {
        super(splitReaderFactory, emitter, context);
        this.schemaChanges = Objects.requireNonNull(schemaChanges, "schemaChanges");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
    }

    @Override
    public final InputStatus pollNext(ReaderOutput<TableRecord> output) throws Exception {
        return schemaChanges.tryCompleteSchemaChange() ? super.pollNext(output) : InputStatus.NOTHING_AVAILABLE;
    }

    @Override
    public final CompletableFuture<Void> isAvailable() {
        CompletableFuture<Void> pending = schemaChanges.getAvailableFuture();
        return pending == null ? super.isAvailable() : pending;
    }

    @Override
    public List<SplitT> snapshotState(long checkpointId) {
        schemaChanges.requireNoPendingSchemaChange();
        return super.snapshotState(checkpointId);
    }

    @Override
    public final void start() {
        context.sendSplitRequest();
    }
}
