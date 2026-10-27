package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;

/** Emits one Binlog event's row images or one frozen snapshot batch with stable identities. */
final class MySqlRecordBatchEmitter {

    private final TableMetrics metrics;
    private final long generation;

    MySqlRecordBatchEmitter(TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
    }

    void emitBinlogRows(
            List<TableRecord> rows,
            String sourceId,
            String eventPosition,
            boolean binlog,
            ReaderOutput<TableRecord> output)
            throws Exception {
        emit(rows, sourceId, eventPosition, null, -1, binlog, output);
    }

    void emitSnapshotRows(
            List<TableRecord> rows, String sourceId, String splitId, int firstRow, ReaderOutput<TableRecord> output)
            throws Exception {
        if (firstRow < 0) {
            throw new IllegalArgumentException("Snapshot output ordinal must not be negative");
        }
        emit(rows, sourceId, null, Objects.requireNonNull(splitId, "splitId"), firstRow, false, output);
    }

    private void emit(
            List<TableRecord> rows,
            String sourceId,
            String eventPosition,
            String snapshotSplitId,
            int firstSnapshotRow,
            boolean binlog,
            ReaderOutput<TableRecord> output)
            throws Exception {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(output, "output");
        int imageCount = rows.size();
        for (int index = 0; index < imageCount; index++) {
            TableRecord row = rows.get(index);
            ChangeEventId identity = snapshotSplitId != null
                    ? ChangeEventId.snapshot(sourceId, snapshotSplitId, firstSnapshotRow + index)
                    : eventPosition == null ? null : new ChangeEventId(sourceId, eventPosition, index, imageCount);
            output.collect(identity == null ? row : row.withChangeEventId(identity));
            metrics.read(generation, row.tableId(), binlog);
        }
    }
}
