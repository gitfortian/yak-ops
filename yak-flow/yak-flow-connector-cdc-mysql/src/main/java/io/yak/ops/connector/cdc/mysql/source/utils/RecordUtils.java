package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotRows;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Snapshot and Binlog reconciliation using the same row-identity rules as Flink CDC RecordUtils. */
public final class RecordUtils {
    private RecordUtils() {}

    /**
     * A chunk is normalized only after its Low-to-High Binlog interval has been consumed.
     * All source rows are detached from JDBC and the buffer is bounded before publication.
     */
    public static final class ChunkBackfill {
        private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

        private final ChunkRange range;
        private final SnapshotRecords records;
        private final int chunkColumn;
        private boolean sealed;

        public ChunkBackfill(ChunkRange range, TableSchema schema, int maxRows) {
            this(range, schema, maxRows, DEFAULT_MAX_BYTES);
        }

        public ChunkBackfill(ChunkRange range, TableSchema schema, int maxRows, long maxBytes) {
            this.range = Objects.requireNonNull(range, "range");
            Objects.requireNonNull(schema, "schema");
            int column = -1;
            for (int index = 0; index < schema.columnCount(); index++) {
                if (schema.column(index).name().equals(range.column())) {
                    if (schema.column(index).nullable()
                            || schema.column(index).dataType().getTypeRoot() != range.type()) {
                        throw new IllegalArgumentException("Snapshot chunk column differs from source schema");
                    }
                    column = index;
                    break;
                }
            }
            if (column < 0) throw new IllegalArgumentException("Unknown snapshot chunk column");
            if ((range.type() == LogicalTypeRoot.CHAR || range.type() == LogicalTypeRoot.VARCHAR)
                    && (range.start() != null || range.end() != null)) {
                throw new IllegalArgumentException("Bounded strings require MySQL collation-aware comparisons");
            }
            chunkColumn = column;
            records = new SnapshotRecords(range.tableId(), schema, maxRows, maxBytes);
        }

        public void addSnapshot(TableRecord row) {
            ensureOpen();
            if (row.rowKind() != RowKind.INSERT) {
                throw new IllegalArgumentException("Snapshot chunk contains a non-insert record");
            }
            if (!contains(row)) throw new IllegalArgumentException("Snapshot row falls outside its chunk");
            records.upsert(row);
        }

        /**
         * Apply a complete Debezium event. UPDATE images are validated together before
         * applying either side; keyed records may move between chunks, whereas a keyless
         * chunk-key change is rejected because the field is not stable row identity.
         */
        public void apply(BinlogEvent event) {
            ensureOpen();
            List<TableRecord> changes = Objects.requireNonNull(event, "event").records();
            if (changes.isEmpty()) return;
            if (changes.size() == 2) {
                TableRecord before = changes.get(0);
                TableRecord after = changes.get(1);
                if (before.rowKind() != RowKind.UPDATE_BEFORE
                        || after.rowKind() != RowKind.UPDATE_AFTER
                        || !before.tableId().equals(after.tableId())) {
                    throw new IllegalArgumentException("Backfill UPDATE images must be adjacent");
                }
                records.check(before);
                records.check(after);
                if (records.keyless()) {
                    Object oldKey = before.row().getField(chunkColumn);
                    Object newKey = after.row().getField(chunkColumn);
                    if (oldKey == null || newKey == null || compare(oldKey, newKey) != 0) {
                        throw new UnsupportedOperationException(
                                "Source without physical primary keys cannot change Chunk Key during Backfill");
                    }
                }
                if (contains(before)) records.delete(before);
                if (contains(after)) records.upsert(after);
            } else if (changes.size() == 1) {
                TableRecord change = changes.getFirst();
                records.check(change);
                switch (change.rowKind()) {
                    case INSERT -> {
                        if (contains(change)) records.upsert(change);
                    }
                    case DELETE -> {
                        if (contains(change)) records.delete(change);
                    }
                    case UPDATE_BEFORE, UPDATE_AFTER ->
                            throw new IllegalArgumentException("Backfill UPDATE requires both before and after");
                }
            } else {
                throw new IllegalArgumentException("Unsupported Binlog change group size");
            }
        }

        public List<TableRecord> finish() {
            ensureOpen();
            sealed = true;
            return records.finish();
        }

        private boolean contains(TableRecord row) {
            records.check(row);
            Object value = row.row().getField(chunkColumn);
            if (value == null) throw new IllegalArgumentException("Snapshot chunk column must not be null");
            if (range.start() == null && range.end() == null) return true;
            return (range.start() == null || compare(value, range.start()) >= 0)
                    && (range.end() == null || compare(value, range.end()) < 0);
        }

        private int compare(Object value, Object boundary) {
            return switch (range.type()) {
                case TINYINT, SMALLINT, INTEGER, BIGINT ->
                        Long.compare(((Number) value).longValue(), ((Number) boundary).longValue());
                case DECIMAL -> ((BigDecimal) value).compareTo((BigDecimal) boundary);
                default -> throw new IllegalArgumentException(
                        "Bounded chunk values require database-aware comparisons");
            };
        }

        private void ensureOpen() {
            if (sealed) throw new IllegalStateException("Snapshot Backfill has already been finalized");
            records.requireOpen();
        }
    }

    /**
     * Primary-keyed records overwrite a single image; keyless records preserve full-row
     * multiplicity, as in Flink CDC's Map<Struct, List<SourceRecord>> snapshot buffer.
     * No synthetic primary key or independent keyless Source execution path is introduced.
     */
    private static final class SnapshotRecords {
        private static final int MAX_TOTAL_ROWS = 10_000_000;

        private final TableId table;
        private final TableSchema schema;
        private final int[] keyPositions;
        private final int maxRows;
        private final long maxBytes;
        private final Map<RowKey, Group> values = new LinkedHashMap<>();
        private long retainedBytes;
        private int totalRows;
        private boolean failed;

        private SnapshotRecords(TableId table, TableSchema schema, int maxRows, long maxBytes) {
            this.table = Objects.requireNonNull(table, "table");
            this.schema = Objects.requireNonNull(schema, "schema");
            if (maxRows < 1 || maxBytes < 1) {
                throw new IllegalArgumentException("Invalid snapshot backfill row or byte budget");
            }
            this.maxRows = maxRows;
            this.maxBytes = maxBytes;
            this.keyPositions = new int[schema.primaryKeys().size()];
            for (int i = 0; i < keyPositions.length; i++) {
                int position = -1;
                for (int j = 0; j < schema.columnCount(); j++) {
                    if (schema.column(j).name().equals(schema.primaryKeys().get(i))) {
                        position = j;
                        break;
                    }
                }
                if (position < 0) throw new IllegalArgumentException("Unknown physical primary-key column");
                keyPositions[i] = position;
            }
        }

        private boolean keyless() {
            return keyPositions.length == 0;
        }

        private void check(TableRecord record) {
            Objects.requireNonNull(record, "record");
            if (!table.equals(record.tableId()) || record.row().getArity() != schema.columnCount()) {
                throw new IllegalArgumentException("Backfill record violates frozen source schema");
            }
            for (int position : keyPositions) {
                if (record.row().isNullAt(position)) {
                    throw new IllegalArgumentException("Physical primary-key column must not be null");
                }
            }
        }

        private void upsert(TableRecord record) {
            requireOpen();
            check(record);
            RowKey key = key(record.row());
            Group previous = values.get(key);
            if (keyless() && previous != null) {
                if (totalRows >= MAX_TOTAL_ROWS) fail();
                previous.copies++;
                totalRows++;
                return;
            }
            TableRecord copy = copy(record);
            long bytes = payloadBytes(copy.row());
            long next = retainedBytes + bytes - (previous == null ? 0 : payloadBytes(previous.record.row()));
            if ((previous == null && values.size() >= maxRows)
                    || next > maxBytes
                    || (keyless() && totalRows >= MAX_TOTAL_ROWS)) {
                fail();
            }
            values.put(key, new Group(copy));
            retainedBytes = next;
            if (previous == null) totalRows++;
        }

        private void delete(TableRecord record) {
            requireOpen();
            check(record);
            RowKey key = key(record.row());
            Group previous = values.get(key);
            if (previous == null) return;
            if (keyless() && previous.copies > 1) {
                previous.copies--;
                totalRows--;
                return;
            }
            values.remove(key);
            retainedBytes -= payloadBytes(previous.record.row());
            totalRows--;
        }

        private List<TableRecord> finish() {
            requireOpen();
            List<TableRecord> output;
            if (keyless()) {
                List<MySqlSnapshotRows.Run> runs = new ArrayList<>(values.size());
                for (Group value : values.values()) {
                    runs.add(new MySqlSnapshotRows.Run(value.record, value.copies));
                }
                output = new MySqlSnapshotRows(runs);
            } else {
                output = values.values().stream().map(group -> group.record).toList();
            }
            values.clear();
            retainedBytes = 0;
            totalRows = 0;
            return output;
        }

        private RowKey key(RowData row) {
            Object[] fields = new Object[keyless() ? schema.columnCount() : keyPositions.length];
            for (int i = 0; i < fields.length; i++) {
                Object value = row.getField(keyless() ? i : keyPositions[i]);
                fields[i] = value instanceof BigDecimal decimal ? decimal.stripTrailingZeros() : detached(value);
            }
            return new RowKey(fields);
        }

        private TableRecord copy(TableRecord record) {
            List<Object> fields = new ArrayList<>(schema.columnCount());
            for (int i = 0; i < schema.columnCount(); i++) {
                fields.add(detached(record.row().getField(i)));
            }
            return new TableRecord(table, RowKind.INSERT, new GenericRowData(fields));
        }

        private static Object detached(Object value) {
            if (value instanceof byte[] bytes) return bytes.clone();
            if (value instanceof java.sql.Timestamp time) {
                java.sql.Timestamp copy = new java.sql.Timestamp(time.getTime());
                copy.setNanos(time.getNanos());
                return copy;
            }
            if (value instanceof java.sql.Date date) return new java.sql.Date(date.getTime());
            if (value instanceof java.sql.Time time) return new java.sql.Time(time.getTime());
            if (value instanceof java.util.Date date) return new java.util.Date(date.getTime());
            return value;
        }

        private long payloadBytes(RowData row) {
            long bytes = 128L + 8L * schema.columnCount();
            for (int i = 0; i < schema.columnCount(); i++) {
                if (row.isNullAt(i)) continue;
                bytes += switch (schema.column(i).dataType().getTypeRoot()) {
                    case CHAR, VARCHAR -> 48L + 2L * row.getString(i).length();
                    case BINARY, VARBINARY -> 24L + ((byte[]) row.getField(i)).length;
                    default -> 64L;
                };
            }
            return bytes;
        }

        private void fail() {
            failed = true;
            values.clear();
            retainedBytes = 0;
            totalRows = 0;
            throw new IllegalStateException(
                    "Snapshot Backfill buffer limit exceeded; reduce Chunk Size");
        }

        private void requireOpen() {
            if (failed) throw new IllegalStateException("Snapshot Backfill has failed");
        }
    }

    private static final class Group {
        private final TableRecord record;
        private int copies = 1;

        private Group(TableRecord record) {
            this.record = record;
        }
    }

    private static final class RowKey {
        private final Object[] values;

        private RowKey(Object[] values) {
            this.values = values;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RowKey key && Arrays.deepEquals(values, key.values);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(values);
        }
    }
}
