package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.SourceSchemaChange;
import java.util.List;
import java.util.Objects;

/**
 * One Debezium change event with its indivisible before/after output and resume position.
 *
 * <p>UPDATE_BEFORE and UPDATE_AFTER remain adjacent inside a single mailbox emission,
 * so a checkpoint barrier cannot be inserted between the two rows.
 */
public record BinlogEvent(List<TableRecord> records, DebeziumOffset offset, SourceSchemaChange schemaChange) {

    public BinlogEvent(List<TableRecord> records, DebeziumOffset offset) {
        this(records, offset, null);
    }

    public BinlogEvent {
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        Objects.requireNonNull(offset, "offset");
        if (schemaChange != null && !records.isEmpty()) {
            throw new IllegalArgumentException("DDL boundary must not contain table row records");
        }
    }

    /** Reports batch size without exposing changed rows or source offsets. */
    @Override
    public String toString() {
        return "BinlogEvent{recordCount=" + records.size() + ", schemaChange=" + (schemaChange != null)
                + ", offset=redacted}";
    }
}
