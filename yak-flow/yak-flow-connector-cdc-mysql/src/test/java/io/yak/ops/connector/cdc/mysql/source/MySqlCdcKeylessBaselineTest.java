package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.types.LogicalTypeRoot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.reader.BinlogSplitReader;
import io.yak.ops.connector.cdc.mysql.source.utils.RecordUtils;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.MySqlCdcSourceFactory;
import io.yak.ops.connector.jdbc.sink.JdbcSinkSchemaValidator;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Ensures unsupported source schemas and legacy BIGINT snapshot inputs fail closed
 * without weakening supported typed split planning and durable snapshot transport.
 */
class MySqlCdcKeylessBaselineTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of());
    private static final TableSchema KEYED = new TableSchema(KEYLESS.columns(), List.of("id"));

    @Test
    void rejectsKeylessTablesBeforeOpeningAnySourceConnections() {
        var factory = new MySqlCdcSourceFactory();
        assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(KEYLESS));
        assertThrows(IllegalArgumentException.class, () -> MySqlChunkSplitter.splitKey(KEYLESS));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlSourceConfig(
                        "localhost", 3306, "user", "secret", 6101, "baseline", Map.of(TABLE, KEYLESS), 256));
        assertTrue(failure.getMessage().toLowerCase(java.util.Locale.ROOT).contains("chunk key"));
    }

    @Test
    void admitsNumericCompositePrimaryKeysAndRejectsUnsafeTypes() {
        var varcharKey = new TableSchema(
                List.of(new Column("id", LogicalTypes.varchar(64).copy(false))), List.of("id"));
        var nullableKey = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT)), List.of("id"));
        var compositeKey = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("tenant", LogicalTypes.BIGINT.copy(false))),
                List.of("tenant", "id"));

        var factory = new MySqlCdcSourceFactory();
        for (TableSchema schema : List.of(varcharKey, nullableKey)) {
            assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(schema));
        }
        factory.validateSchema(compositeKey);
        assertEquals("id", MySqlChunkSplitter.splitKey(KEYED));
        factory.validateSchema(KEYED);
    }

    @Test
    void keylessBackfillFilterAndJdbcChangelogSinkFailClosed() {
        var split = new MySqlSnapshotSplit("chunk", "fingerprint", TABLE, null, null, null);
        var high = offset(200);

        assertThrows(IllegalArgumentException.class, () -> new RecordUtils.ChunkBackfill(new ChunkRange(TABLE, "missing", LogicalTypeRoot.BIGINT, null, null), KEYLESS, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> BinlogSplitReader.fromFinishedSplits(
                        Map.of(TABLE, KEYLESS),
                        List.of(new FinishedSnapshotSplitInfo(TABLE, "chunk", null, null, high))));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validate(KEYLESS, KEYED, JdbcWriteMode.UPSERT));
    }

    @Test
    void durableSnapshotTransportPreservesIdenticalKeylessRows() throws Exception {
        // Snapshot transport preserves repeated physical rows across durable checkpoints.
        // An ordinary Map<RowData, RowData> must not replace this list.
        var duplicate = new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(7L, "same"));
        var other = new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(8L, "other"));
        var prepared = new MySqlSnapshotSplit("chunk", "fingerprint", TABLE, null, null, null)
                .withOutput(new MySqlSnapshotOutput(offset(100), offset(200), List.of(duplicate, duplicate, other)));
        var serializer = new MySqlHybridSplitSerializer();
        var restored = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(prepared));

        assertEquals(3, restored.output().rows().size());
        assertEquals("same", restored.output().rows().get(0).row().getString(1));
        assertEquals("same", restored.output().rows().get(1).row().getString(1));
        assertEquals(7L, restored.output().rows().get(0).row().getLong(0));
        assertEquals(7L, restored.output().rows().get(1).row().getLong(0));
        assertEquals(prepared, restored);
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
