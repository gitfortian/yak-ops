package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Filters changes already reflected in the normalized snapshot using the matched chunk's High
 * watermark, independently for the before and after images of an UPDATE.
 *
 * <p>The legacy constructor reads BIGINT primary-key checkpoint metadata without changing its
 * wire format. Typed chunk metadata is planning-only until versioned split/Enumerator checkpoints
 * and Snapshot readers can persist and restore typed boundaries.
 */
public final class MySqlBinlogSplitFilter {

    /** Typed filtering fence; persisted transport will be introduced with checkpoint migration. */
    public record FinishedChunk(String splitId, ChunkRange range, BinlogOffset highWatermark) {
        public FinishedChunk {
            if (splitId == null || splitId.isBlank()) {
                throw new IllegalArgumentException("Finished chunk must have a stable split identity");
            }
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(highWatermark, "highWatermark");
        }
    }

    private record ChunkColumn(String name, LogicalTypeRoot type, int position, int columnCount) {}

    private final Map<TableId, List<FinishedChunk>> completed = new LinkedHashMap<>();
    private final Map<TableId, ChunkColumn> columns = new LinkedHashMap<>();
    private final boolean legacyPrimaryKey;

    /** Existing checkpoint-backed path; its BIGINT primary-key admission remains unchanged. */
    public MySqlBinlogSplitFilter(Map<TableId, TableSchema> schemas, List<FinishedSnapshotSplitInfo> finished) {
        this(schemas, legacyChunks(schemas, finished), true);
    }

    /**
     * Filters using the explicitly chosen chunk column rather than a source physical primary key.
     *
     * <p>Bounded strings remain unsupported because Java string comparison does not implement
     * MySQL's collation. A single unbounded string chunk is valid without comparing strings.
     */
    public static MySqlBinlogSplitFilter forChunks(Map<TableId, TableSchema> schemas, List<FinishedChunk> finished) {
        return new MySqlBinlogSplitFilter(schemas, finished, false);
    }

    private MySqlBinlogSplitFilter(
            Map<TableId, TableSchema> schemas, List<FinishedChunk> finished, boolean legacyPrimaryKey) {
        Objects.requireNonNull(schemas, "schemas");
        Objects.requireNonNull(finished, "finished");
        if (schemas.isEmpty() || finished.isEmpty()) {
            throw new IllegalArgumentException("Binlog filtering requires completed snapshot metadata");
        }
        this.legacyPrimaryKey = legacyPrimaryKey;
        for (var entry : schemas.entrySet()) {
            completed.put(entry.getKey(), new ArrayList<>());
        }
        Set<String> ids = new HashSet<>();
        for (FinishedChunk chunk : finished) {
            if (!ids.add(chunk.splitId())) {
                throw new IllegalArgumentException("Duplicate completed snapshot chunk identity");
            }
            TableId table = chunk.range().tableId();
            List<FinishedChunk> chunks = completed.get(table);
            if (chunks == null) throw new IllegalArgumentException("Unknown completed snapshot table");
            TableSchema schema = schemas.get(table);
            ChunkRange range = chunk.range();
            Column column = schema.columns().stream()
                    .filter(candidate -> candidate.name().equals(range.column()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown snapshot chunk column"));
            if (column.nullable() || column.dataType().getTypeRoot() != range.type()) {
                throw new IllegalArgumentException("Snapshot chunk column differs from the source schema");
            }
            int position = -1;
            for (int i = 0; i < schema.columnCount(); i++) {
                if (schema.column(i).name().equals(range.column())) {
                    position = i;
                    break;
                }
            }
            if (legacyPrimaryKey
                    && (schema.primaryKeys().size() != 1
                            || !schema.primaryKeys().getFirst().equals(range.column())
                            || range.type() != LogicalTypeRoot.BIGINT)) {
                throw new IllegalArgumentException("Binlog filtering requires exactly one BIGINT primary key");
            }
            ChunkColumn previous = columns.putIfAbsent(
                    table, new ChunkColumn(range.column(), range.type(), position, schema.columnCount()));
            if (previous != null && (!previous.name().equals(range.column()) || previous.type() != range.type())) {
                throw new IllegalArgumentException("Finished chunks use different snapshot chunk columns");
            }
            if ((range.type() == LogicalTypeRoot.CHAR || range.type() == LogicalTypeRoot.VARCHAR)
                    && (range.start() != null || range.end() != null)) {
                throw new IllegalArgumentException(
                        "Bounded string chunks require MySQL collation-aware Binlog comparisons");
            }
            chunks.add(chunk);
        }
        completed.replaceAll((table, chunks) -> {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("Missing completed snapshot chunks for " + table);
            }
            chunks.sort(Comparator.comparing(
                    (FinishedChunk chunk) -> chunk.range().start(),
                    Comparator.nullsFirst(
                            (left, right) -> compare(columns.get(table).type(), left, right))));
            if (chunks.getFirst().range().start() != null
                    || chunks.getLast().range().end() != null) {
                throw new IllegalArgumentException("Finished snapshot ranges do not cover the entire keyspace");
            }
            for (int index = 1; index < chunks.size(); index++) {
                Object upper = chunks.get(index - 1).range().end();
                Object lower = chunks.get(index).range().start();
                if (upper == null || lower == null || compare(columns.get(table).type(), upper, lower) != 0) {
                    throw new IllegalArgumentException("Finished snapshot ranges have a gap or overlap");
                }
            }
            return List.copyOf(chunks);
        });
    }

    private static List<FinishedChunk> legacyChunks(
            Map<TableId, TableSchema> schemas, List<FinishedSnapshotSplitInfo> finished) {
        Objects.requireNonNull(schemas, "schemas");
        Objects.requireNonNull(finished, "finished");
        List<FinishedChunk> result = new ArrayList<>(finished.size());
        for (FinishedSnapshotSplitInfo item : finished) {
            TableSchema schema = schemas.get(item.tableId());
            if (schema == null) throw new IllegalArgumentException("Unknown completed snapshot table");
            if (schema.primaryKeys().size() != 1) {
                throw new IllegalArgumentException("Binlog filtering requires exactly one primary key");
            }
            String key = schema.primaryKeys().getFirst();
            result.add(new FinishedChunk(
                    item.splitId(),
                    new ChunkRange(
                            item.tableId(), key, LogicalTypeRoot.BIGINT, item.lowerInclusive(), item.upperExclusive()),
                    item.highWatermark()));
        }
        return result;
    }

    /** An empty result retains the original offset so the caller can advance progress. */
    public BinlogEvent filter(BinlogEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.schemaChange() != null) {
            validateSchemaChange(event);
            return event;
        }
        List<TableRecord> rows = event.records();
        if (rows.isEmpty()) return event;
        if (rows.size() == 2) {
            TableRecord before = rows.get(0);
            TableRecord after = rows.get(1);
            if (before.rowKind() != RowKind.UPDATE_BEFORE
                    || after.rowKind() != RowKind.UPDATE_AFTER
                    || !before.tableId().equals(after.tableId())) {
                throw new IllegalArgumentException("Binlog filter requires complete adjacent UPDATE pairs");
            }
            // Evaluate and validate both images before returning any partial change.
            boolean keepBefore = shouldEmit(before, event);
            boolean keepAfter = shouldEmit(after, event);
            if (keepBefore && keepAfter) return event;
            if (keepBefore) {
                return new BinlogEvent(
                        List.of(new TableRecord(before.tableId(), RowKind.DELETE, before.row())), event.offset());
            }
            if (keepAfter) {
                return new BinlogEvent(
                        List.of(new TableRecord(after.tableId(), RowKind.INSERT, after.row())), event.offset());
            }
            return new BinlogEvent(List.of(), event.offset());
        }
        if (rows.size() != 1
                || rows.getFirst().rowKind() == RowKind.UPDATE_BEFORE
                || rows.getFirst().rowKind() == RowKind.UPDATE_AFTER) {
            throw new IllegalArgumentException("Binlog filter requires atomic change events");
        }
        return shouldEmit(rows.getFirst(), event) ? event : new BinlogEvent(List.of(), event.offset());
    }

    private void validateSchemaChange(BinlogEvent event) {
        var change = event.schemaChange();
        ChunkColumn previous = columns.get(change.tableId());
        if (previous == null
                || !change.before().primaryKeys().equals(change.after().primaryKeys())
                || (legacyPrimaryKey && change.after().primaryKeys().size() != 1)) {
            throw new UnsupportedOperationException("MySQL CDC cannot change Binlog filter primary keys");
        }
        int position = -1;
        for (int i = 0; i < change.after().columns().size(); i++) {
            var column = change.after().columns().get(i);
            if (column.name().equals(previous.name())) {
                if (column.type().getTypeRoot() != previous.type() || column.type().isNullable()) {
                    throw new UnsupportedOperationException("MySQL CDC cannot change the snapshot chunk column type");
                }
                position = i;
                break;
            }
        }
        if (position < 0) {
            throw new UnsupportedOperationException("MySQL CDC schema change removed the snapshot chunk column");
        }
        columns.put(
                change.tableId(),
                new ChunkColumn(previous.name(), previous.type(), position, change.after().columns().size()));
    }

    private boolean shouldEmit(TableRecord record, BinlogEvent event) {
        List<FinishedChunk> chunks = completed.get(record.tableId());
        ChunkColumn column = columns.get(record.tableId());
        if (chunks == null || column == null) {
            throw new IllegalArgumentException("Binlog event references an unregistered source table");
        }
        if (record.row().getArity() != column.columnCount() || record.row().isNullAt(column.position())) {
            throw new IllegalArgumentException("Binlog row violates its frozen schema or chunk column");
        }
        Object value = record.row().getField(column.position());
        int left = 0;
        int right = chunks.size() - 1;
        int found = 0;
        // Find the greatest inclusive lower bound using only supported scalar semantics.
        while (left <= right) {
            int middle = left + (right - left) / 2;
            Object lower = chunks.get(middle).range().start();
            if (lower == null || compare(column.type(), lower, value) <= 0) {
                found = middle;
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }
        FinishedChunk match = chunks.get(found);
        Object upper = match.range().end();
        if (upper != null && compare(column.type(), value, upper) >= 0) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog chunk key");
        }
        return event.offset().compareTo(match.highWatermark()) > 0;
    }

    private static int compare(LogicalTypeRoot type, Object left, Object right) {
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> Long.compare(toLong(left), toLong(right));
            case DECIMAL -> toDecimal(left).compareTo(toDecimal(right));
            default -> throw new IllegalArgumentException("Chunk key requires database-aware comparison: " + type);
        };
    }

    private static long toLong(Object value) {
        if (value instanceof Long number) return number;
        if (value instanceof Integer number) return number.longValue();
        if (value instanceof Short number) return number.longValue();
        if (value instanceof Byte number) return number.longValue();
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Binlog numeric chunk key has an unexpected type");
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException("Binlog numeric chunk key exceeds signed BIGINT range", error);
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        throw new IllegalArgumentException("Binlog decimal chunk key must be a BigDecimal");
    }
}
