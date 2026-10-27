package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Stable Debezium coordinates and atomic UPDATE identity across Source emission. */
class MySqlChangeEventIdsTest {
    private static final TableId TABLE = new TableId("shop", null, "entries");

    @Test
    void reorderingOffsetMapsOrIntegralNumberTypesNeverChangesIdentity() {
        var partition = new LinkedHashMap<String, Object>();
        partition.put("server", "yak");
        partition.put("connector", "mysql");
        var position = new LinkedHashMap<String, Object>();
        position.put("file", "mysql-bin.000004");
        position.put("pos", 600L);
        position.put("event", 3);
        position.put("row", 1L);
        position.put("gtids", "abc:1-2");
        var first = new DebeziumOffset(partition, position);
        var reversed = new DebeziumOffset(
                Map.of("connector", "mysql", "server", "yak"),
                Map.of("gtids", "abc:1-2", "row", 1, "event", 3L,
                        "pos", 600, "file", "mysql-bin.000004"));
        assertEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(reversed));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(partition, Map.of("file", "mysql-bin.000004",
                        "pos", 600, "event", 3, "row", 2))));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(partition, Map.of("file", "mysql-bin.000005",
                        "pos", 600, "event", 3, "row", 1))));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "other"), position)));
    }

    @Test
    void incompleteOffsetsCannotBePresentedAsReplaySafeIdentities() {
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.1", "pos", 42))));
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"),
                        Map.of("file", "mysql-bin.1", "pos", 42, "event", 1))));
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("gtids", "abc:1", "event", 1, "row", 1))));
    }

    @Test
    void updateImagesShareEventIdentityButKeepDifferentImageIndexesAndCheckpointOrder() throws Exception {
        DebeziumOffset position = position(44, 3);
        BinlogEvent update = new BinlogEvent(
                List.of(row(RowKind.UPDATE_BEFORE, "same"), row(RowKind.UPDATE_AFTER, "same")),
                position);
        var state = new MySqlCdcSplitState(new MySqlBinlogSplit("task-definition", null, new byte[0]));
        var emitter = new MySqlCdcRecordEmitter(new SchemaChangeHandler(context()));
        List<TableRecord> delivered = new ArrayList<>();
        emitter.emitRecord(update, delivered::add, state);
        assertEquals(2, delivered.size());
        ChangeEventId before = delivered.getFirst().changeEventId();
        ChangeEventId after = delivered.getLast().changeEventId();
        assertTrue(before.sameEvent(after));
        assertEquals(0, before.imageIndex());
        assertEquals(1, after.imageIndex());
        assertEquals(2, before.imageCount());
        assertEquals(2, after.imageCount());
        assertEquals(position, state.checkpoint(new byte[] {1}).offset());

        var restored = new MySqlCdcSplitState(
                new MySqlBinlogSplit("task-definition", position(42, 1), new byte[] {1}));
        List<TableRecord> replayed = new ArrayList<>();
        emitter.emitRecord(update, replayed::add, restored);
        assertEquals(before, replayed.getFirst().changeEventId());
        assertEquals(after, replayed.getLast().changeEventId());
        assertEquals(position, restored.checkpoint(new byte[] {1}).offset());
        assertEquals("same", replayed.getFirst().row().getString(0));

        var failed = new MySqlCdcSplitState(
                new MySqlBinlogSplit("task-definition", position(42, 1), new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> emitter.emitRecord(update, record -> {
            if (record.rowKind() == RowKind.UPDATE_AFTER) throw new IllegalStateException("sink failed");
        }, failed));
        assertEquals(position(42, 1), failed.checkpoint(new byte[] {1}).offset());
    }


    @Test
    void frozenSnapshotIdentityUsesDurableSplitAndRowOrdinalsAfterRestore() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9001)
                .topicPrefix("identity-test")
                .table(TABLE, schema)
                .build()
                .config();
        var splitReader = new MySqlHybridSplitReader(config);
        var emitter = new MySqlHybridRecordEmitter(
                context(), splitReader, new TableMetrics(), new SchemaChangeHandler(context()));
        var rows = List.of(
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)),
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)));
        var original = new MySqlSnapshotSplit("chunk-0", "stable-definition", TABLE, null, null, null)
                .withOutput(new MySqlSnapshotOutput(position(10, 0), position(20, 0), rows));
        var state = new MySqlHybridSplitState(original);
        List<TableRecord> first = new ArrayList<>();
        emitter.emitRecord(MySqlHybridFetchedRecord.snapshotRows(original.output().rows().subList(0, 1), 1),
                first::add, state);
        assertEquals(ChangeEventId.snapshot("stable-definition", "chunk-0", 0), first.getFirst().changeEventId());

        var serializer = new MySqlHybridSplitSerializer();
        var checkpoint = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(state.checkpoint(null)));
        assertEquals(1, checkpoint.outputPosition());
        var resumed = new MySqlHybridSplitState(checkpoint);
        List<TableRecord> next = new ArrayList<>();
        emitter.emitRecord(MySqlHybridFetchedRecord.snapshotRows(checkpoint.output().rows().subList(1, 2), 2),
                next::add, resumed);
        assertEquals(ChangeEventId.snapshot("stable-definition", "chunk-0", 1), next.getFirst().changeEventId());
        assertFalse(first.getFirst().changeEventId().sameEvent(next.getFirst().changeEventId()));
        List<TableRecord> replay = new ArrayList<>();
        emitter.emitRecord(MySqlHybridFetchedRecord.snapshotRows(original.output().rows().subList(0, 1), 1),
                replay::add, new MySqlHybridSplitState(original));
        assertEquals(first.getFirst().changeEventId(), replay.getFirst().changeEventId());
        splitReader.close();
    }

    @Test
    void binlogRowsShareIdentityAcrossModesAndAdvanceOnlyAfterWholeEvent() throws Exception {
        DebeziumOffset previous = position(42, 1);
        DebeziumOffset offset = position(44, 3);
        DebeziumOffset high = position(50, 5);
        List<TableRecord> rows = List.of(row(RowKind.UPDATE_BEFORE, "same"), row(RowKind.UPDATE_AFTER, "same"));

        var cdcState = new MySqlCdcSplitState(new MySqlBinlogSplit("task-definition", previous, new byte[] {1}));
        List<TableRecord> cdcOutput = new ArrayList<>();
        new MySqlCdcRecordEmitter(new SchemaChangeHandler(context()))
                .emitRecord(new BinlogEvent(rows, offset), cdcOutput::add, cdcState);

        var finished = new FinishedSnapshotSplitInfo(TABLE, "chunk-0", null, null, high);
        var streaming = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("task-definition", previous, new byte[] {1}),
                MySqlHybridBinlogSplit.Phase.STREAMING,
                high,
                List.of(finished));
        var hybridState = new MySqlHybridSplitState(streaming);
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100).copy(false))),
                List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9002)
                .topicPrefix("hybrid-emitter-contract")
                .table(TABLE, schema)
                .build()
                .config();
        try (var splitReader = new MySqlHybridSplitReader(config)) {
            var emitter = new MySqlHybridRecordEmitter(
                    context(), splitReader, new TableMetrics(), new SchemaChangeHandler(context()));
            List<TableRecord> hybridOutput = new ArrayList<>();
            var event = new MySqlHybridFetchedRecord(rows, null, offset, false, null, null);
            emitter.emitRecord(event, hybridOutput::add, hybridState);

            assertEquals(
                    cdcOutput.stream().map(TableRecord::changeEventId).toList(),
                    hybridOutput.stream().map(TableRecord::changeEventId).toList());
            assertEquals(offset, cdcState.checkpoint(new byte[] {1}).offset());
            assertEquals(offset, ((MySqlHybridBinlogSplit) hybridState.checkpoint(new byte[] {1})).binlog().offset());

            var failedState = new MySqlHybridSplitState(streaming);
            assertThrows(IllegalStateException.class, () -> emitter.emitRecord(event, record -> {
                if (record.rowKind() == RowKind.UPDATE_AFTER) {
                    throw new IllegalStateException("downstream failed");
                }
            }, failedState));
            assertEquals(
                    previous,
                    ((MySqlHybridBinlogSplit) failedState.checkpoint(new byte[] {1})).binlog().offset());
        }
    }

    @Test
    void keyedLegacyOffsetsDeliverWithoutInventingIdentity() throws Exception {
        var state = new MySqlCdcSplitState(new MySqlBinlogSplit("task-definition", null, new byte[0]));
        var output = new ArrayList<TableRecord>();
        BinlogEvent update = new BinlogEvent(List.of(row(RowKind.INSERT, "legacy")),
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.1", "pos", 8)));
        new MySqlCdcRecordEmitter(new SchemaChangeHandler(context())).emitRecord(update, output::add, state);
        assertNull(output.getFirst().changeEventId());
        assertEquals(update.offset(), state.checkpoint(new byte[] {1}).offset());
    }

    private static TableRecord row(RowKind kind, String value) {
        return new TableRecord(TABLE, kind, GenericRowData.of(value));
    }

    private static DebeziumOffset position(long offset, int row) {
        return new DebeziumOffset(Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000004", "pos", offset, "event", 1, "row", row));
    }

    private static SourceReaderContext context() {
        return new SourceReaderContext() {
            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public int getIndexOfSubtask() {
                return 0;
            }

            @Override
            public int currentParallelism() {
                return 1;
            }

            @Override
            public void sendSplitRequest() {}
        };
    }
}
