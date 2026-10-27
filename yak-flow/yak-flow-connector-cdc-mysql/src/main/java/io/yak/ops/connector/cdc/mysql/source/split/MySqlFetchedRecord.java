package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.List;
import java.util.Objects;

/** One mailbox step: a prepared chunk, frozen output batch, Binlog change, or bootstrap anchor. */
public record MySqlFetchedRecord(
        List<TableRecord> rows,
        DebeziumOffset binlogOffset,
        boolean lowWatermark,
        MySqlSnapshotSplit preparedSnapshot,
            SourceSchemaChange schemaChange) {

    public MySqlFetchedRecord {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        int kinds = (binlogOffset == null ? 0 : 1)
                + (preparedSnapshot == null ? 0 : 1);
        if (kinds != 1
                || (preparedSnapshot != null && (preparedSnapshot.output() == null || !rows.isEmpty()))
                || (lowWatermark && (binlogOffset == null || !rows.isEmpty()))
                || (schemaChange != null && (binlogOffset == null || !rows.isEmpty() || lowWatermark))) {
            throw new IllegalArgumentException("Inconsistent MySQL fetched record kind");
        }
    }

    public static MySqlFetchedRecord binlog(BinlogEvent event) {
        return new MySqlFetchedRecord(
                event.records(), event.offset(), false, null, event.schemaChange());
    }

    public static MySqlFetchedRecord lowWatermark(DebeziumOffset offset) {
        return new MySqlFetchedRecord(List.of(), Objects.requireNonNull(offset, "offset"), true, null, null);
    }

    public static MySqlFetchedRecord prepared(MySqlSnapshotSplit split) {
        return new MySqlFetchedRecord(List.of(), null, false, split, null);
    }

    @Override
    public String toString() {
        int count = preparedSnapshot == null ? rows.size() : preparedSnapshot.output().rows().size();
        return "MySqlFetchedRecord{rowCount=" + count + ", values=redacted}";
    }
}
