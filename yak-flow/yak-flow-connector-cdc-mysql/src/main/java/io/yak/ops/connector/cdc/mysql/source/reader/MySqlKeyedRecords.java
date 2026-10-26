package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.core.data.GenericRowData;
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
 * Snapshot reconciliation by physical primary key. Later row images overwrite earlier
 * images for the same key; the Chunk Key can differ from the physical primary key.
 */
final class MySqlKeyedRecords {
    private final TableId table;
    private final TableSchema schema;
    private final int[] positions;
    private final int maxRows;
    private final long maxBytes;
    private final Map<Key, TableRecord> records = new LinkedHashMap<>();
    private long retainedBytes;
    private boolean failed;

    MySqlKeyedRecords(TableId table, TableSchema schema, int maxRows, long maxBytes) {
        this.table = Objects.requireNonNull(table, "table");
        this.schema = Objects.requireNonNull(schema, "schema");
        if (schema.primaryKeys().isEmpty() || maxRows < 1 || maxBytes < 1) {
            throw new IllegalArgumentException("Keyed backfill requires physical primary keys and positive budgets");
        }
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
        positions = new int[schema.primaryKeys().size()];
        for (int i = 0; i < positions.length; i++) {
            int position = -1;
            for (int j = 0; j < schema.columnCount(); j++) {
                if (schema.column(j).name().equals(schema.primaryKeys().get(i))) {
                    position = j;
                    break;
                }
            }
            if (position < 0) throw new IllegalArgumentException("Unknown physical primary-key column");
            positions[i] = position;
        }
    }

    void check(TableRecord record) {
        Objects.requireNonNull(record, "record");
        if (!table.equals(record.tableId()) || record.row().getArity() != schema.columnCount()) {
            throw new IllegalArgumentException("Keyed backfill record violates the frozen source schema");
        }
        for (int position : positions) {
            if (record.row().isNullAt(position)) {
                throw new IllegalArgumentException("Physical primary-key column must not be null");
            }
        }
    }

    void upsert(TableRecord record) {
        requireOpen();
        check(record);
        Key key = key(record);
        TableRecord previous = records.get(key);
        TableRecord snapshot = copy(record);
        long bytes = estimate(snapshot);
        long next = retainedBytes + bytes - (previous == null ? 0 : estimate(previous));
        if ((previous == null && records.size() >= maxRows) || next > maxBytes) {
            failed = true;
            records.clear();
            retainedBytes = 0;
            throw new IllegalStateException("Snapshot Backfill row or byte limit exceeded");
        }
        records.put(key, snapshot);
        retainedBytes = next;
    }

    void delete(TableRecord record) {
        requireOpen();
        check(record);
        TableRecord old = records.remove(key(record));
        if (old != null) retainedBytes -= estimate(old);
    }

    List<TableRecord> finish() {
        requireOpen();
        List<TableRecord> result = List.copyOf(records.values());
        records.clear();
        retainedBytes = 0;
        return result;
    }

    private Key key(TableRecord record) {
        Object[] fields = new Object[positions.length];
        for (int i = 0; i < positions.length; i++) {
            fields[i] = field(record.row().getField(positions[i]));
        }
        return new Key(fields);
    }

    private TableRecord copy(TableRecord record) {
        List<Object> fields = new ArrayList<>(schema.columnCount());
        for (int i = 0; i < schema.columnCount(); i++) {
            fields.add(field(record.row().getField(i)));
        }
        return new TableRecord(table, RowKind.INSERT, new GenericRowData(fields));
    }

    private static Object field(Object value) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros();
        if (value instanceof java.sql.Timestamp timestamp) {
            var copy = new java.sql.Timestamp(timestamp.getTime());
            copy.setNanos(timestamp.getNanos());
            return copy;
        }
        if (value instanceof java.sql.Date date) return new java.sql.Date(date.getTime());
        if (value instanceof java.sql.Time time) return new java.sql.Time(time.getTime());
        return value;
    }

    private long estimate(TableRecord record) {
        long bytes = 256L + schema.columnCount() * 16L;
        for (int i = 0; i < schema.columnCount(); i++) {
            Object value = record.row().getField(i);
            if (value == null) continue;
            bytes += switch (schema.column(i).dataType().getTypeRoot()) {
                case CHAR, VARCHAR -> 96L + 4L * record.row().getString(i).length();
                case BINARY, VARBINARY -> 48L + 2L * ((byte[]) value).length;
                default -> 128L;
            };
        }
        return bytes;
    }

    private void requireOpen() {
        if (failed) throw new IllegalStateException("Snapshot Backfill has failed");
    }

    private record Key(Object[] values) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Arrays.deepEquals(values, key.values);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(values);
        }
    }
}
