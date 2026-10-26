package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The configured chunk column is table-scoped, but unsupported hybrid execution still fails closed. */
class MySqlChunkKeySelectionTest {
    private static final TableId ORDERS = new TableId("shop", null, "orders");
    private static final TableId ITEMS = new TableId("shop", null, "items");
    private static final TableSchema ORDERS_SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("created_at", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("id"));
    private static final TableSchema ITEMS_SCHEMA = new TableSchema(
            List.of(
                    new Column("sequence", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("sequence"));

    @Test
    void choosesConfiguredNonPrimaryColumnIndependentOfPrimaryKey() {
        assertEquals("id", ChunkUtils.getChunkKeyColumn(ORDERS_SCHEMA, null).name());
        assertEquals("created_at", ChunkUtils.getChunkKeyColumn(ORDERS_SCHEMA, "created_at").name());
        var keyless = new TableSchema(ORDERS_SCHEMA.columns(), List.of());
        assertEquals("created_at", ChunkUtils.getChunkKeyColumn(keyless, "created_at").name());
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, null));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, "missing"));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, ""));
        var nullable = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT)), List.of("id"));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(nullable, "id"));
    }

    @Test
    void hybridRuntimeStillRequiresTheSoleNonNullBigintPrimaryKey() {
        assertEquals("id", MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, null));
        assertEquals("id", MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, "id"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, "created_at"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlCdcSourceFactory().validateSchema(
                        options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "created_at").toReadonlyConfig(),
                        ORDERS_SCHEMA));
        var keyless = new TableSchema(ORDERS_SCHEMA.columns(), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlChunkSplitter.splitKey(keyless, "created_at"));
    }

    @Test
    void factoryKeepsPerTableSelectionsWithoutRelaxingSharedConnectionSettings() throws Exception {
        var factory = new MySqlCdcSourceFactory();
        var measurements = new TableMetrics();
        var first = new TableSourceFactoryContext(
                options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "id").toReadonlyConfig(),
                ORDERS,
                ORDERS_SCHEMA,
                measurements);
        var second = new TableSourceFactoryContext(
                options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "sequence").toReadonlyConfig(),
                ITEMS,
                ITEMS_SCHEMA,
                measurements);
        var source = (MySqlHybridCdcSource) factory.createSource(List.of(first, second));
        Field field = MySqlHybridCdcSource.class.getDeclaredField("config");
        field.setAccessible(true);
        var config = (MySqlCdcSourceConfig) field.get(source);
        assertEquals(Map.of(ORDERS, "id", ITEMS, "sequence"), config.chunkKeyColumns());
        assertEquals(config.chunkKeyColumns(), config.withoutSchemaEvolution().chunkKeyColumns());

        var changedConnection = new TableSourceFactoryContext(
                options()
                        .set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3308/shop")
                        .set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "sequence")
                        .toReadonlyConfig(),
                ITEMS,
                ITEMS_SCHEMA,
                measurements);
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, changedConnection)));
    }

    @Test
    void factoryRejectsUnknownOrUnsupportedOverrideBeforeConstructingHybridSource() {
        var factory = new MySqlCdcSourceFactory();
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(
                        options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "unknown").toReadonlyConfig(),
                        ORDERS,
                        ORDERS_SCHEMA)));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(
                        options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "created_at").toReadonlyConfig(),
                        ORDERS,
                        ORDERS_SCHEMA)));
    }

    @Test
    void explicitChunkColumnChangesSourceIdentityButTheDefaultRemainsUnconfigured() throws Exception {
        var implicit = builder().table(ORDERS, ORDERS_SCHEMA).build();
        var explicit = builder().table(ORDERS, ORDERS_SCHEMA, "id").build();
        assertTrue(implicit.config().chunkKeyColumns().isEmpty());
        assertEquals(Map.of(ORDERS, "id"), explicit.config().chunkKeyColumns());
        var field = MySqlCdcSource.class.getDeclaredField("fingerprint");
        field.setAccessible(true);
        assertNotEquals(field.get(implicit), field.get(explicit));
    }

    private static Configuration options() {
        return new Configuration()
                .set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3307/shop")
                .set(JdbcConnectionOptions.USERNAME, "user")
                .set(JdbcConnectionOptions.PASSWORD, "password")
                .set(MySqlCdcSourceOptions.SERVER_ID, 6130)
                .set(MySqlCdcSourceOptions.TOPIC_PREFIX, "chunk-test");
    }

    private static MySqlCdcSourceBuilder builder() {
        return MySqlCdcSource.builder()
                .hostname("localhost")
                .username("user")
                .password("password")
                .serverId(6130)
                .topicPrefix("chunk-test");
    }
}
