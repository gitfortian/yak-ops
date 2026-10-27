package io.yak.ops.connector.cdc.mysql.source;


import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfigFactory;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlHybridEnumeratorStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlPendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Verifies the unified entry keeps the existing mode-specific runtime and checkpoint contracts. */
class MySqlSourceEntryTest {

    @Test
    void initialUsesOriginalHybridStateAndSerializers() {
        var source = MySqlSource.create(builder(), StartupOptions.initial(), 512, new TableMetrics());
        assertEquals(StartupMode.INITIAL, source.startupMode());
        assertEquals(StartupOptions.initial(), source.startupOptions());
        assertEquals(512, source.snapshotChunkSize());
        assertInstanceOf(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.class, source.getSplitSerializer());
        assertInstanceOf(io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer.class, source.getEnumeratorCheckpointSerializer());
    }

    @Test
    void latestUsesOriginalBinlogStateAndSerializers() {
        var source = MySqlSource.create(builder(), StartupOptions.latest(), 512, new TableMetrics());
        assertEquals(StartupMode.LATEST_OFFSET, source.startupMode());
        assertEquals(StartupOptions.latest(), source.startupOptions());
        assertThrows(IllegalStateException.class, source::snapshotChunkSize);
        assertInstanceOf(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.class, source.getSplitSerializer());
        assertInstanceOf(io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer.class, source.getEnumeratorCheckpointSerializer());
    }

    private static MySqlSourceConfigFactory builder() {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        return MySqlSource.builder()
                .hostname("localhost")
                .username("test")
                .password("test")
                .serverId(8123)
                .topicPrefix("mysql-source-entry")
                .table(new TableId("shop", null, "orders"), schema);
    }
}
