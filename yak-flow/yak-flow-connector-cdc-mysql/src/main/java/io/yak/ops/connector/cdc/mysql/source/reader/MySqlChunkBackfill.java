package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Reconciles one bounded snapshot chunk with the ordered Binlog interval between its watermarks.
 *
 * <p>INSERT and UPDATE_AFTER replace the row for a key,
 * while DELETE and UPDATE_BEFORE retract the old key. Both halves of a primary-key-changing
 * UPDATE are applied together. Rows are emitted only after the bounded backfill has ended.
 *
 * <p>This class has no JDBC, Debezium or Runtime dependencies. The owning snapshot task is
 * responsible for reading the complete Low-to-High interval; the buffer must never infer
 * completion from an idle Binlog stream. No partially merged rows may reach the mailbox.
 */
public final class MySqlChunkBackfill {

    private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;
    private final MySqlSnapshotSplit split;
    private final ChunkRange range;
    private final MySqlKeylessRecords keyless;
    private final MySqlKeyedRecords keyed;
    private final int chunkColumn;
    private final TableSchema schema;
    private final long maxBytes;
    private long retainedBytes;
    private boolean failed;
    private final int keyPosition;
    private final int columns;
    private final int maxRows;
    private final Map<Long, TableRecord> rows = new TreeMap<>();
    private boolean sealed;

    /**
     * Creates a bounded, restart-from-scratch chunk accumulator.
     *
     * @param split original half-open primary-key range
     * @param schema immutable source column order
     * @param maxRows maximum normalized rows retained in memory
     */
    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows) {
        this(split, schema, maxRows, DEFAULT_MAX_BYTES);
    }

    /**
     * Bounds retained rows and conservatively accounted payload bytes for one chunk.
     *
     * @param split frozen chunk identity and range
     * @param schema source column types used for variable-width payload accounting
     * @param maxRows maximum number of normalized rows
     * @param maxBytes retained payload budget, not an exact JVM heap measurement
     */
    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows, long maxBytes) {
        this.split = Objects.requireNonNull(split, "split");
        this.range = null;
        this.keyless = null;
        this.keyed = null;
        this.chunkColumn = -1;
        this.schema = Objects.requireNonNull(schema, "schema");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Chunk backfill must have a positive byte budget");
        }
        this.maxBytes = maxBytes;
        if (maxRows < 1) {
            throw new IllegalArgumentException("Chunk backfill must have a positive row limit");
        }
        if (schema.primaryKeys().size() != 1) {
            throw new IllegalArgumentException("Chunk backfill requires one source primary key");
        }
        String key = schema.primaryKeys().getFirst();
        int position = -1;
        for (int index = 0; index < schema.columnCount(); index++) {
            if (schema.column(index).name().equals(key)) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            throw new IllegalArgumentException("Chunk backfill primary key is not part of the source schema");
        }
        this.keyPosition = position;
        this.columns = schema.columnCount();
        this.maxRows = maxRows;
    }

    /**
     * Holds typed Snapshot rows by their physical primary key when present, or preserves
     * complete row-image multiplicity for tables without physical primary keys.
     *
     * <p>Bounded string ranges are intentionally rejected: MySQL collation comparisons
     * cannot be reproduced reliably with Java String.compareTo.
     */
    public MySqlChunkBackfill(ChunkRange range, TableSchema schema, int maxRows) {
        this(range, schema, maxRows, DEFAULT_MAX_BYTES);
    }

    public MySqlChunkBackfill(ChunkRange range, TableSchema schema, int maxRows, long maxBytes) {
        this.range = Objects.requireNonNull(range, "range");
        this.schema = Objects.requireNonNull(schema, "schema");
        int position = -1;
        for (int index = 0; index < schema.columnCount(); index++) {
            if (schema.column(index).name().equals(range.column())) {
                position = index;
                if (schema.column(index).dataType().getTypeRoot() != range.type()
                        || schema.column(index).nullable()) {
                    throw new IllegalArgumentException("Keyless chunk column differs from the source schema");
                }
                break;
            }
        }
        if (position < 0) throw new IllegalArgumentException("Unknown keyless chunk column");
        if ((range.type() == io.yak.ops.core.types.LogicalTypeRoot.CHAR
                        || range.type() == io.yak.ops.core.types.LogicalTypeRoot.VARCHAR)
                && (range.start() != null || range.end() != null)) {
            throw new IllegalArgumentException(
                    "Bounded string chunk backfill requires MySQL collation-aware filtering");
        }
        if (maxRows < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid chunk backfill budget");
        this.split = null;
        this.keyPosition = -1;
        this.chunkColumn = position;
        this.columns = schema.columnCount();
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
        this.keyless = schema.primaryKeys().isEmpty()
                ? new MySqlKeylessRecords(range.tableId(), schema, maxRows, maxBytes)
                : null;
        this.keyed = schema.primaryKeys().isEmpty()
                ? null
                : new MySqlKeyedRecords(range.tableId(), schema, maxRows, maxBytes);
    }

    /** Buffers initial INSERT rows without exposing intermediate snapshot contents. */
    public void addSnapshot(TableRecord record) {
        ensureOpen();
        if (record.rowKind() != RowKind.INSERT) {
            throw new IllegalArgumentException("Snapshot chunk contains a non-insert record");
        }
        if (range != null) {
            if (!contains(record)) throw new IllegalArgumentException("Snapshot row falls outside its chunk");
            if (keyed != null) keyed.upsert(record);
            else keyless.insert(record);
            return;
        }
        long key = key(record);
        if (!contains(key)) {
            throw new IllegalArgumentException("Snapshot row falls outside its chunk");
        }
        retain(key, record);
    }

    /**
     * Applies a complete Debezium change; adjacent before/after images are indivisible.
     *
     * <p>Primary-key moves can cross chunk boundaries. The old key is removed from its
     * original chunk, and the new key is added only to the chunk containing the new value.
     */
    public void apply(BinlogEvent event) {
        ensureOpen();
        Objects.requireNonNull(event, "event");
        List<TableRecord> changes = event.records();
        if (changes.isEmpty()) {
            return;
        }
        if (changes.size() == 2) {
            TableRecord before = changes.get(0);
            TableRecord after = changes.get(1);
            if (before.rowKind() != RowKind.UPDATE_BEFORE || after.rowKind() != RowKind.UPDATE_AFTER) {
                throw new IllegalArgumentException("Backfill UPDATE images must be adjacent");
            }
            // Validate both images before mutating the accumulator.
            if (range != null) {
                if (keyed != null) {
                    keyed.check(before);
                    keyed.check(after);
                    if (contains(before)) keyed.delete(before);
                    if (contains(after)) keyed.upsert(after);
                } else {
                    keyless.check(before);
                    keyless.check(after);
                    Object beforeKey = before.row().getField(chunkColumn);
                    Object afterKey = after.row().getField(chunkColumn);
                    if (beforeKey == null || afterKey == null || compare(beforeKey, afterKey) != 0) {
                        throw new UnsupportedOperationException(
                                "Source without physical primary keys cannot change Chunk Key during Backfill");
                    }
                    if (contains(before)) keyless.delete(before);
                    if (contains(after)) keyless.insert(after);
                }
            } else {
                key(before);
                key(after);
                retract(before);
                upsert(after);
            }
        } else if (changes.size() == 1) {
            TableRecord change = changes.getFirst();
            switch (change.rowKind()) {
                case INSERT -> {
                    if (range == null) upsert(change);
                    else if (contains(change)) {
                        if (keyed != null) keyed.upsert(change);
                        else keyless.insert(change);
                    }
                }
                case DELETE -> {
                    if (range == null) retract(change);
                    else if (contains(change)) {
                        if (keyed != null) keyed.delete(change);
                        else keyless.delete(change);
                    }
                }
                case UPDATE_BEFORE, UPDATE_AFTER ->
                    throw new IllegalArgumentException("Backfill UPDATE requires both before and after");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Binlog change group size");
        }
    }

    /**
     * Seals the normalized chunk after the Binlog reader proves it reached the High watermark.
     *
     * @return normalized INSERT records, preserving duplicate row multiplicity for keyless tables
     */
    public List<TableRecord> finish() {
        ensureOpen();
        sealed = true;
        List<TableRecord> result =
                range == null ? List.copyOf(rows.values()) : keyed != null ? keyed.finish() : keyless.finish();
        rows.clear();
        retainedBytes = 0;
        return result;
    }

    private boolean contains(TableRecord record) {
        Object value = record.row().getField(chunkColumn);
        if (value == null) throw new IllegalArgumentException("Snapshot chunk column must not be null");
        if (range.start() == null && range.end() == null) return true;
        int aboveStart = range.start() == null ? 1 : compare(value, range.start());
        int belowEnd = range.end() == null ? -1 : compare(value, range.end());
        return aboveStart >= 0 && belowEnd < 0;
    }

    private int compare(Object value, Object boundary) {
        return switch (range.type()) {
            case TINYINT, SMALLINT, INTEGER, BIGINT ->
                Long.compare(((Number) value).longValue(), ((Number) boundary).longValue());
            case DECIMAL -> ((BigDecimal) value).compareTo((BigDecimal) boundary);
            default ->
                throw new IllegalArgumentException("Bounded keyless chunk values require database-aware comparisons");
        };
    }

    private void retract(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            TableRecord removed = rows.remove(key);
            if (removed != null) retainedBytes -= payloadBytes(removed.row());
        }
    }

    private void upsert(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            retain(key, record);
        }
    }

    private TableRecord snapshot(TableRecord record) {
        RowData value = record.row();
        List<Object> fields = new ArrayList<>(columns);
        for (int index = 0; index < columns; index++) {
            fields.add(value.getField(index));
        }
        return new TableRecord(split.tableId(), RowKind.INSERT, new GenericRowData(fields));
    }

    private long key(TableRecord record) {
        Objects.requireNonNull(record, "record");
        if (!split.tableId().equals(record.tableId()) || record.row().getArity() != columns) {
            throw new IllegalArgumentException("Backfill event violates the frozen source table schema");
        }
        if (record.row().isNullAt(keyPosition)) {
            throw new IllegalArgumentException("Backfill primary key must not be null");
        }
        return record.row().getLong(keyPosition);
    }

    private boolean contains(long key) {
        return (split.lowerInclusive() == null || key >= split.lowerInclusive())
                && (split.upperExclusive() == null || key < split.upperExclusive());
    }

    private void retain(long key, TableRecord record) {
        TableRecord previous = rows.get(key);
        long size = payloadBytes(record.row());
        long nextBytes = retainedBytes - (previous == null ? 0 : payloadBytes(previous.row())) + size;
        if ((previous == null && rows.size() >= maxRows) || nextBytes > maxBytes) {
            failed = true;
            rows.clear();
            retainedBytes = 0;
            throw new IllegalStateException("Snapshot Backfill buffer limit exceeded; use smaller chunks");
        }
        rows.put(key, snapshot(record));
        retainedBytes = nextBytes;
    }

    private long payloadBytes(RowData row) {
        long bytes = 128L + 8L * columns;
        for (int index = 0; index < columns; index++) {
            if (row.isNullAt(index)) continue;
            bytes += switch (schema.column(index).dataType().getTypeRoot()) {
                case CHAR, VARCHAR -> 48L + 2L * row.getString(index).length();
                case BINARY, VARBINARY -> 24L + ((byte[]) row.getField(index)).length;
                default -> 64L;
            };
        }
        return bytes;
    }

    private void ensureOpen() {
        if (sealed || failed) {
            throw new IllegalStateException("Snapshot Backfill has already been finalized or failed");
        }
    }
}
