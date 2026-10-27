package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Chunk Key selection follows physical table metadata, not a persisted keyless flag. */
class MySqlChunkKeyAdmissionTest {
    private static final TableSchema WITHOUT_PK = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(16, 2)),
                    new Column("name", LogicalTypes.varchar(60).copy(false))),
            List.of());

    @Test
    void requiresExplicitNonNullNumericChunkColumnWhenPrimaryKeyIsAbsent() {
        assertEquals("bucket", ChunkUtils.requireSupportedSnapshot(WITHOUT_PK, "bucket").name());
        assertEquals("name", ChunkUtils.getChunkKeyColumn(WITHOUT_PK, "name").name());
        for (String invalid : new String[] {null, "missing", "amount", "name", ""}) {
            assertThrows(IllegalArgumentException.class, () -> ChunkUtils.requireSupportedSnapshot(WITHOUT_PK, invalid));
        }
    }

    @Test
    void derivesSourceModeFromPhysicalSchemaWithoutOptInFlag() {
        var factory = new MySqlCdcSourceFactory();
        var settings = new Configuration().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "bucket");
        factory.validateSchema(settings.toReadonlyConfig(), WITHOUT_PK);
        assertThrows(IllegalArgumentException.class,
                () -> factory.validateSchema(new Configuration().toReadonlyConfig(), WITHOUT_PK));
        var keyed = new TableSchema(WITHOUT_PK.columns(), List.of("bucket"));
        factory.validateSchema(new Configuration().toReadonlyConfig(), keyed);
        assertEquals("bucket", MySqlChunkSplitter.splitKey(keyed));
        assertThrows(IllegalArgumentException.class,
                () -> factory.validateSchema(settings.set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "name")
                        .toReadonlyConfig(), keyed));
    }

    @Test
    void hybridAdmitsNoPhysicalPrimaryKeyWithExplicitChunkKey() {
        var table = new io.yak.ops.core.data.TableId("app", null, "events");
        var source = MySqlSource.builder()
                .hostname("localhost").username("replication").password("test")
                .serverId(7731).topicPrefix("no-physical-primary-key")
                .table(table, WITHOUT_PK, "bucket").buildHybrid(2);
        assertEquals(io.yak.ops.core.api.connector.source.Boundedness.CONTINUOUS_UNBOUNDED,
                source.getBoundedness());
        assertThrows(IllegalArgumentException.class, () -> MySqlSource.builder()
                .hostname("localhost").username("replication").password("test")
                .serverId(7732).topicPrefix("missing-chunk-key")
                .table(table, WITHOUT_PK).buildHybrid(2));
    }

    @Test
    void rejectsUnresolvedAndNullableChunkFields() {
        var unresolved = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.decimal(null, null))), List.of());
        var nullable = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.BIGINT)), List.of());
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.requireSupportedSnapshot(unresolved, "bucket"));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.requireSupportedSnapshot(nullable, "bucket"));
    }
}
