package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotRows;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Retains the multiplicity of full-row values in a keyless MySQL snapshot chunk.
 *
 * <p>Flink CDC's RecordUtils stores lists for keyless rows because two identical row images
 * must not collapse into one map entry. A DELETE retracts exactly one image. Duplicate
 * full-row images share one immutable representative and a multiplicity count; only
 * distinct row images consume the retained-image budget. No artificial key is assigned.
 */
final class MySqlKeylessRecords {
    private final TableId table;
    private final TableSchema schema;
    private final int maxRows;
    private final long maxBytes;
    private final Map<RowValues, Group> rows = new LinkedHashMap<>();
    private static final int MAX_TOTAL_ROWS = 10_000_000;
    private int count;
    private long retainedBytes;
    private boolean failed;

    MySqlKeylessRecords(TableId table, TableSchema schema, int maxRows, long maxBytes) {
        this.table = Objects.requireNonNull(table, "table");
        this.schema = Objects.requireNonNull(schema, "schema");
        if (maxRows < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid keyless backfill budget");
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
    }

    void insert(TableRecord record) {
        requireOpen();
        TableRecord copy = copy(record);
        long size = payloadBytes(copy.row());
        RowValues key = values(copy.row());
        Group existing = rows.get(key);
        if (count >= MAX_TOTAL_ROWS
                || (existing == null && (rows.size() >= maxRows || size > maxBytes - retainedBytes))) {
            failed = true;
            rows.clear();
            count = 0;
            retainedBytes = 0;
            throw new IllegalStateException(
                    "Snapshot Backfill distinct-row or retained-memory limit exceeded; reducing Chunk Size may not split equal keys");
        }
        if (existing == null) {
            rows.put(key, new Group(copy));
            retainedBytes += size;
        } else {
            existing.copies++;
        }
        count++;
    }

    void delete(TableRecord record) {
        requireOpen();
        check(record);
        RowValues key = values(record.row());
        Group matches = rows.get(key);
        if (matches == null) return;
        count--;
        if (--matches.copies == 0) {
            retainedBytes -= payloadBytes(matches.record.row());
            rows.remove(key);
        }
    }

    List<TableRecord> finish() {
        requireOpen();
        List<MySqlSnapshotRows.Run> runs = new ArrayList<>(rows.size());
        for (Group group : rows.values()) runs.add(new MySqlSnapshotRows.Run(group.record, group.copies));
        rows.clear();
        count = 0;
        retainedBytes = 0;
        return new MySqlSnapshotRows(runs);
    }

    void check(TableRecord record) {
        Objects.requireNonNull(record, "record");
        if (!table.equals(record.tableId()) || record.row().getArity() != schema.columnCount()) {
            throw new IllegalArgumentException("Keyless backfill event violates the frozen source schema");
        }
    }

    private TableRecord copy(TableRecord record) {
        check(record);
        List<Object> fields = new ArrayList<>(schema.columnCount());
        for (int index = 0; index < schema.columnCount(); index++) {
            fields.add(field(record.row().getField(index)));
        }
        return new TableRecord(table, RowKind.INSERT, new GenericRowData(fields));
    }

    private static Object field(Object value) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof java.sql.Timestamp timestamp) {
            java.sql.Timestamp copy = new java.sql.Timestamp(timestamp.getTime());
            copy.setNanos(timestamp.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date date) return new java.sql.Date(date.getTime());
        if (value instanceof java.sql.Time time) return new java.sql.Time(time.getTime());
        if (value instanceof java.util.Date date) return new java.util.Date(date.getTime());
        return value;
    }

    private RowValues values(RowData data) {
        Object[] fields = new Object[schema.columnCount()];
        for (int index = 0; index < fields.length; index++) {
            Object value = field(data.getField(index));
            fields[index] = value instanceof BigDecimal decimal ? decimal.stripTrailingZeros() : value;
        }
        return new RowValues(fields);
    }

    /**
     * Conservatively counts both the retained row and its full-row comparison key.
     * This budget is not a measurement of exact JVM heap usage.
     */
    private long payloadBytes(RowData data) {
        long bytes = 256L + 16L * schema.columnCount();
        for (int index = 0; index < schema.columnCount(); index++) {
            if (data.isNullAt(index)) continue;
            bytes += switch (schema.column(index).dataType().getTypeRoot()) {
                case CHAR, VARCHAR -> 96L + 4L * data.getString(index).length();
                case BINARY, VARBINARY -> 48L + 2L * ((byte[]) data.getField(index)).length;
                default -> 128L;
            };
        }
        return bytes;
    }

    private void requireOpen() {
        if (failed) throw new IllegalStateException("Snapshot Backfill has failed");
    }

    private static final class Group {
        private final TableRecord record;
        private int copies = 1;

        private Group(TableRecord record) {
            this.record = record;
        }
    }

    private static final class RowValues {
        private final Object[] fields;

        RowValues(Object[] fields) {
            this.fields = fields;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RowValues value && Arrays.deepEquals(fields, value.fields);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(fields);
        }
    }
}
