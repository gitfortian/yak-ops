package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Emits snapshot rows and indivisible Binlog mutations before mutating mailbox checkpoint state.
 *
 * <p>The initial low-watermark event has no rows. Only after its Schema History and offset
 * are recorded does the coordinator receive a signal to begin planning snapshot splits.
 */
public final class MySqlHybridRecordEmitter
        implements RecordEmitter<MySqlHybridFetchedRecord, TableRecord, MySqlHybridSplitState> {

    private final SourceReaderContext context;
    private final SchemaChangeHandler schemaChanges;
    private final MySqlHybridSplitReader splits;
    private final TableMetrics metrics;
    private final long generation;

    public MySqlHybridRecordEmitter(
            SourceReaderContext context,
            MySqlHybridSplitReader splits,
            TableMetrics metrics,
            SchemaChangeHandler schemaChanges) {
        this.schemaChanges = Objects.requireNonNull(schemaChanges, "schemaChanges");
        this.metrics = metrics;
        generation = metrics.generation();
        this.context = Objects.requireNonNull(context, "context");
        this.splits = Objects.requireNonNull(splits, "splits");
    }

    @Override
    public void emitRecord(
            MySqlHybridFetchedRecord event, ReaderOutput<TableRecord> output, MySqlHybridSplitState state)
            throws Exception {
        if (event.schemaChange() != null) {
            schemaChanges.submitSchemaChange(event.schemaChange(), () -> state.emitBinlog(event.binlogOffset()));
            return;
        }
        if (event.preparedSnapshot() != null) {
            state.prepareSnapshot(event.preparedSnapshot());
            return;
        }
        int count = event.rows().size();
        String eventPosition = event.binlogOffset() != null && !event.lowWatermark()
                ? MySqlChangeEventIds.fromBinlog(event.binlogOffset())
                : null;
        int firstSnapshotRow = event.snapshotRowsEmitted() == null ? -1 : event.snapshotRowsEmitted() - count;
        if (firstSnapshotRow < 0 && event.snapshotRowsEmitted() != null) {
            throw new IllegalStateException("Snapshot output ordinal is smaller than the emitted batch");
        }
        for (int index = 0; index < count; index++) {
            TableRecord row = event.rows().get(index);
            ChangeEventId identity = firstSnapshotRow >= 0
                    ? ChangeEventId.snapshot(state.sourceId(), state.snapshotSplitId(), firstSnapshotRow + index)
                    : eventPosition == null ? null : new ChangeEventId(state.sourceId(), eventPosition, index, count);
            output.collect(identity == null ? row : row.withChangeEventId(identity));
            metrics.read(generation, row.tableId(), state.binlog());
        }
        metrics.observed(generation);
        if (event.snapshotRowsEmitted() != null) {
            state.emitSnapshotRows(event.snapshotRowsEmitted());
        } else if (event.snapshotKey() != null) {
            state.emitSnapshotKey(event.snapshotKey());
        } else if (event.lowWatermark()) {
            state.captureLow(event.binlogOffset(), splits.snapshotHistory());
            context.sendSourceEventToCoordinator(new MySqlLowWatermarkEvent(event.binlogOffset()));
            context.sendSplitRequest();
        } else {
            state.emitBinlog(event.binlogOffset());
        }
    }
}
