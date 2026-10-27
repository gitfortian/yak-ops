package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.List;
import java.util.Objects;

/** One mailbox step: a prepared chunk, bounded output batch, complete Binlog change, or bootstrap anchor. */
public record MySqlFetchedRecord(
        List<TableRecord> rows,
        Long snapshotKey,
        DebeziumOffset binlogOffset,
        boolean lowWatermark,
        MySqlSnapshotSplit preparedSnapshot,
        Integer snapshotRowsEmitted,
        SourceSchemaChange schemaChange) {

    public MySqlFetchedRecord(
            List<TableRecord> rows,
            Long snapshotKey,
            DebeziumOffset binlogOffset,
            boolean lowWatermark,
            MySqlSnapshotSplit preparedSnapshot,
            Integer snapshotRowsEmitted) {
        this(rows, snapshotKey, binlogOffset, lowWatermark, preparedSnapshot, snapshotRowsEmitted, null);
    }

    public MySqlFetchedRecord(
            List<TableRecord> rows, Long snapshotKey, DebeziumOffset binlogOffset, boolean lowWatermark) {
        this(rows, snapshotKey, binlogOffset, lowWatermark, null, null);
    }

    public MySqlFetchedRecord {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        int kinds = (snapshotKey == null ? 0 : 1)
                + (binlogOffset == null ? 0 : 1)
                + (preparedSnapshot == null ? 0 : 1)
                + (snapshotRowsEmitted == null ? 0 : 1);
        if (kinds != 1
                || (snapshotRowsEmitted != null && snapshotRowsEmitted < 0)
                || (preparedSnapshot != null && (preparedSnapshot.output() == null || !rows.isEmpty()))
                || (lowWatermark && (binlogOffset == null || !rows.isEmpty()))
                || (schemaChange != null && (binlogOffset == null || !rows.isEmpty() || lowWatermark))) {
            throw new IllegalArgumentException("Inconsistent MySQL fetched record kind");
        }
    }

    public static MySqlFetchedRecord binlog(BinlogEvent event) {
        return new MySqlFetchedRecord(event.records(), null, event.offset(), false, null, null, event.schemaChange());
    }

    public static MySqlFetchedRecord prepared(MySqlSnapshotSplit split) {
        return new MySqlFetchedRecord(List.of(), null, null, false, split, null);
    }

    public static MySqlFetchedRecord snapshotRows(List<TableRecord> rows, int position) {
        return new MySqlFetchedRecord(rows, null, null, false, null, position);
    }

    @Override
    public String toString() {
        return "MySqlFetchedRecord{rowCount=" + rows.size() + ", values=redacted}";
    }
}
