package io.yak.ops.connector.cdc.mysql.debezium.reader;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlBinlogSplitReadTask;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.utils.ObjectUtils;
import io.yak.ops.connector.cdc.mysql.source.utils.SplitKeyUtils;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * <p>Only typed snapshot chunk ranges are accepted; a Chunk Key is not necessarily row identity.
 */
public final class BinlogSplitReader {

    /** Completed chunk fence, derived from the versioned persisted Split metadata. */
    public record FinishedChunk(String splitId, ChunkRange range, DebeziumOffset highWatermark) {
        public FinishedChunk {
            if (splitId == null || splitId.isBlank()) {
                throw new IllegalArgumentException("Finished chunk must have a stable split identity");
            }
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(highWatermark, "highWatermark");
        }
    }

    private record ChunkColumn(String name, LogicalTypeRoot type, int position, int columnCount) {}

    private final Map<TableId, List<FinishedSnapshotSplitInfo>> completed = new LinkedHashMap<>();
    private final Map<TableId, ChunkColumn> columns = new LinkedHashMap<>();
    private final Map<TableId, DebeziumOffset> maxHighWatermarks = new LinkedHashMap<>();
    private final Set<TableId> pureBinlogPhaseTables = new HashSet<>();
    private final Set<TableId> schemasWithPrimaryKey = new HashSet<>();

    /**
     * Filters using the explicitly chosen chunk column rather than a source physical primary key.
     *
     * <p>Bounded strings remain unsupported because Java string comparison does not implement
     * MySQL's collation. A single unbounded string chunk is valid without comparing strings.
     */
    public static BinlogSplitReader forChunks(Map<TableId, TableSchema> schemas, List<FinishedChunk> finished) {
        return new BinlogSplitReader(schemas, finished);
    }

    /** Creates the checkpoint-backed handoff filter for typed chunks across all selected tables. */
    public static BinlogSplitReader fromFinishedSplits(
            Map<TableId, TableSchema> schemas, List<FinishedSnapshotSplitInfo> finished) {
        Objects.requireNonNull(finished, "finished");
        List<FinishedChunk> converted = new ArrayList<>(finished.size());
        for (FinishedSnapshotSplitInfo info : finished) {
            ChunkRange range = info.chunkRange();
            if (range == null) {
                throw new IllegalArgumentException("Legacy BIGINT snapshot metadata cannot be used for Binlog handoff");
            }
            converted.add(new FinishedChunk(info.splitId(), range, info.highWatermark()));
        }
        return forChunks(schemas, converted);
    }

    private BinlogSplitReader(
            Map<TableId, TableSchema> schemas, List<FinishedChunk> finished) {
        Objects.requireNonNull(schemas, "schemas");
        Objects.requireNonNull(finished, "finished");
        if (schemas.isEmpty() || finished.isEmpty()) {
            throw new IllegalArgumentException("Binlog filtering requires completed snapshot metadata");
        }
        for (var entry : schemas.entrySet()) {
            completed.put(entry.getKey(), new ArrayList<>());
            if (!entry.getValue().primaryKeys().isEmpty()) schemasWithPrimaryKey.add(entry.getKey());
        }
        Set<String> ids = new HashSet<>();
        for (FinishedChunk chunk : finished) {
            if (!ids.add(chunk.splitId())) {
                throw new IllegalArgumentException("Duplicate completed snapshot chunk identity");
            }
            TableId table = chunk.range().tableId();
            List<FinishedSnapshotSplitInfo> chunks = completed.get(table);
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
            chunks.add(new FinishedSnapshotSplitInfo(
                    table, chunk.splitId(), null, null, chunk.highWatermark(), range));
        }
        completed.replaceAll((table, chunks) -> {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("Missing completed snapshot chunks for " + table);
            }
            SplitKeyUtils.sortFinishedSplitInfos(chunks);
            if (chunks.getFirst().chunkRange().start() != null
                    || chunks.getLast().chunkRange().end() != null) {
                throw new IllegalArgumentException("Finished snapshot ranges do not cover the entire keyspace");
            }
            for (int index = 1; index < chunks.size(); index++) {
                Object upper = chunks.get(index - 1).chunkRange().end();
                Object lower = chunks.get(index).chunkRange().start();
                if (upper == null || lower == null || compare(columns.get(table).type(), upper, lower) != 0) {
                    throw new IllegalArgumentException("Finished snapshot ranges have a gap or overlap");
                }
            }
            maxHighWatermarks.put(
                    table,
                    chunks.stream()
                            .map(FinishedSnapshotSplitInfo::highWatermark)
                            .max(DebeziumOffset::compareTo)
                            .orElseThrow());
            return List.copyOf(chunks);
        });
    }

    /** Poll the Debezium engine and apply the completed snapshot chunk watermarks. */
    public BinlogEvent poll(MySqlBinlogSplitReadTask engine) throws Exception {
        BinlogEvent event = Objects.requireNonNull(engine, "engine").poll();
        return event == null ? null : filter(event);
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
            // A keyless Chunk Key is not a stable row identity; moving it while
            // different completed chunks have different High watermarks is unsafe.
            ChunkColumn chunk = columns.get(before.tableId());
            if (chunk != null
                    && chunkValueDiffers(chunk, before, after)
                    && !schemasWithPrimaryKey.contains(before.tableId())) {
                throw new UnsupportedOperationException(
                        "Keyless Binlog handoff cannot apply an UPDATE that changes its Chunk Key");
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

    private boolean chunkValueDiffers(ChunkColumn chunk, TableRecord before, TableRecord after) {
        if (before.row().getArity() != chunk.columnCount()
                || after.row().getArity() != chunk.columnCount()
                || before.row().isNullAt(chunk.position())
                || after.row().isNullAt(chunk.position())) {
            throw new IllegalArgumentException("Binlog UPDATE violates the frozen chunk column");
        }
        return compare(
                        chunk.type(),
                        before.row().getField(chunk.position()),
                        after.row().getField(chunk.position()))
                != 0;
    }

    private void validateSchemaChange(BinlogEvent event) {
        var change = event.schemaChange();
        if (!schemasWithPrimaryKey.contains(change.tableId())) {
            throw new UnsupportedOperationException("Keyless CDC schema change requires coordinated acceptance");
        }
        ChunkColumn previous = columns.get(change.tableId());
        if (previous == null || !change.before().primaryKeys().equals(change.after().primaryKeys())) {
            throw new UnsupportedOperationException("MySQL CDC cannot change Binlog filter primary keys");
        }
        int position = -1;
        for (int i = 0; i < change.after().columns().size(); i++) {
            var column = change.after().columns().get(i);
            if (column.name().equals(previous.name())) {
                if (column.type().getTypeRoot() != previous.type()
                        || column.type().isNullable()) {
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
                new ChunkColumn(
                        previous.name(),
                        previous.type(),
                        position,
                        change.after().columns().size()));
    }

    private boolean shouldEmit(TableRecord record, BinlogEvent event) {
        List<FinishedSnapshotSplitInfo> chunks = completed.get(record.tableId());
        ChunkColumn column = columns.get(record.tableId());
        if (chunks == null || column == null) {
            throw new IllegalArgumentException("Binlog event references an unregistered source table");
        }
        if (record.row().getArity() != column.columnCount() || record.row().isNullAt(column.position())) {
            throw new IllegalArgumentException("Binlog row violates its frozen schema or chunk column");
        }
        Object value = record.row().getField(column.position());
        // Validate the scalar type even after the fast path no longer needs a range lookup.
        validateChunkValue(column.type(), value);
        if (pureBinlogPhaseTables.contains(record.tableId())) {
            return true;
        }
        if (event.offset().compareTo(maxHighWatermarks.get(record.tableId())) > 0) {
            pureBinlogPhaseTables.add(record.tableId());
            return true;
        }
        // The Flink CDC utility owns the sorted, half-open chunk lookup.
        FinishedSnapshotSplitInfo match =
                SplitKeyUtils.findSplitByKeyBinary(chunks, new Object[] {value});
        if (match == null) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog chunk key");
        }
        return event.offset().compareTo(match.highWatermark()) > 0;
    }

    public boolean isPureBinlogPhase(TableId tableId) {
        return pureBinlogPhaseTables.contains(tableId);
    }

    private static void validateChunkValue(LogicalTypeRoot type, Object value) {
        switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> toLong(value);
            case DECIMAL -> toDecimal(value);
            case CHAR, VARCHAR -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("String chunk key has an unexpected type");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported Binlog chunk key type: " + type);
        }
    }

    private static int compare(LogicalTypeRoot type, Object left, Object right) {
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> ObjectUtils.compare(toLong(left), toLong(right));
            case DECIMAL -> ObjectUtils.compare(toDecimal(left), toDecimal(right));
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
