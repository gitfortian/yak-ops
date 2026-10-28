package io.yak.ops.connector.cdc.mysql.debezium.reader;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flink-style MySQL Snapshot fetch-size contract: JDBC and mailbox batches use the configured
 * size; all rows must be delivered exactly once and the completion marker arrives only last.
 */
class SnapshotSplitReaderTest {

    private static final TableId TABLE = new TableId(null, null, "orders");

    @Test
    void usesConfiguredFetchSizeAcrossSnapshotBatches() throws Exception {
        String url = "jdbc:h2:mem:fetch_size_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO orders VALUES (1),(2),(3),(4),(5)");
        }
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var split = MySqlSnapshotSplit.typed(
                "orders-0", new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null));
        try (var reader = new SnapshotSplitReader(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                Map.of(TABLE, schema),
                2)) {
            reader.open(split);
            var sizes = new ArrayList<Integer>();
            var ids = new ArrayList<Long>();
            for (int batchNumber = 0; batchNumber < 3; batchNumber++) {
                var batch = reader.fetch();
                assertThat(batch.nextSplit()).isEqualTo(split.splitId());
                int rows = 0;
                io.yak.ops.core.data.TableRecord record;
                while ((record = batch.nextRecordFromSplit()) != null) {
                    rows++;
                    ids.add((Long) record.row().getField(0));
                }
                sizes.add(rows);
                assertThat(batch.finishedSplits().contains(split.splitId())).isEqualTo(batchNumber == 2);
                batch.recycle();
            }
            assertThat(sizes).containsExactly(2, 2, 1);
            assertThat(ids).containsExactly(1L, 2L, 3L, 4L, 5L);
        }
    }

    @Test
    void rejectsInvalidFetchSizeBeforeOpeningDatabase() {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        assertThatThrownBy(() -> new SnapshotSplitReader(
                        () -> {
                            throw new AssertionError("JDBC should not be used for invalid fetch size");
                        },
                        JdbcDialects.forUrl("jdbc:mysql:"),
                        Map.of(TABLE, schema),
                        0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
