package io.yak.ops.connector.cdc.mysql.source.assigners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotSplitReader;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The active snapshot planner checkpoints a Chunk boundary, never a partial row cursor. */
class MySqlChunkSplitterTest {

    @Test
    void plansSparseHalfOpenRangesAndRestartsIncompleteChunkFromItsLowerBoundary() throws Exception {
        String url = "jdbc:h2:mem:hybrid_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(url);
                var statement = c.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            statement.execute("INSERT INTO orders VALUES (1,'a'),(100,'b'),(1000,'c'),(5000,'d'),(6000,'e')");
        }
        TableId table = new TableId(null, null, "orders");
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100))),
                List.of("id"));
        var dialect = JdbcDialects.forUrl("jdbc:mysql:");
        var splitter = new MySqlChunkSplitter(() -> DriverManager.getConnection(url), dialect, Map.of(table, schema), 2);
        var first = splitter.planNext(table, "fp", 0, 0, null);
        assertNull(first.chunkRange().start());
        assertEquals(1000L, first.chunkRange().end());
        var cursor = new ChunkRange(table, "id", LogicalTypeRoot.BIGINT, first.chunkRange().end(), null);
        var second = splitter.planNext(table, "fp", 0, 1, cursor);
        assertEquals(1000L, second.chunkRange().start());
        assertEquals(6000L, second.chunkRange().end());
        var last = splitter.planNext(table, "fp", 0, 2,
                new ChunkRange(table, "id", LogicalTypeRoot.BIGINT, second.chunkRange().end(), null));
        assertNull(last.chunkRange().end());

        // An incomplete Chunk is rescanned from its original half-open bounds.
        // It must not skip equal-key rows based on a previous emit position.
        var observed = new ArrayList<Long>();
        try (var reader = new SnapshotSplitReader(
                () -> DriverManager.getConnection(url), dialect, Map.of(table, schema))) {
            reader.open(first);
            var partial = reader.fetch();
            assertEquals(first.splitId(), partial.nextSplit());
            var row = partial.nextRecordFromSplit();
            observed.add(row.row().getLong(0));
        }
        try (var restarted = new SnapshotSplitReader(
                () -> DriverManager.getConnection(url), dialect, Map.of(table, schema))) {
            restarted.open(first);
            var whole = restarted.fetch();
            assertEquals(first.splitId(), whole.nextSplit());
            assertEquals(1L, whole.nextRecordFromSplit().row().getLong(0));
            assertEquals(100L, whole.nextRecordFromSplit().row().getLong(0));
            assertNull(whole.nextRecordFromSplit());
            assertTrue(whole.finishedSplits().contains(first.splitId()));
        }
        assertEquals(List.of(1L), observed);
    }
}
