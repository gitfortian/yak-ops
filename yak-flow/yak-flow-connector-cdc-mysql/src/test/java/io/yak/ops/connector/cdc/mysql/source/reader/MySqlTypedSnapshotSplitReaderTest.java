package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotSplitReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests raw typed snapshot scanning before Backfill/Checkpoint makes the result durable. */
class MySqlTypedSnapshotSplitReaderTest {
    private static final TableId TABLE = new TableId(null, null, "entries");

    @Test
    void scansEveryKeylessDuplicateInDisjointDecimalRangesWithoutLongConversion() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (bucket DECIMAL(30,8) NOT NULL, payload VARCHAR(60))");
            statement.execute("INSERT INTO entries VALUES "
                    + "(1.25000000,'same'),(1.25000000,'same'),(1.25000000,'same'),"
                    + "(9223372036854775808.00000000,'other'),"
                    + "(9223372036854775808.00000000,'other'),(9223372036854775809.00000000,NULL)");
        }
        var schema = new TableSchema(
                List.of(
                        new Column("bucket", LogicalTypes.decimal(30, 8).copy(false)),
                        new Column("payload", LogicalTypes.varchar(60))),
                List.of());
        List<ChunkRange> ranges = planner(url, schema, "bucket", 2).splitChunks(TABLE);
        assertEquals(3, ranges.size());
        assertEquals(new BigDecimal("9223372036854775808.00000000"), ranges.getFirst().end());
        assertEquals(ranges.getFirst().end(), ranges.get(1).start());
        assertEquals(ranges.get(1).end(), ranges.get(2).start());
        List<TableRecord> all = new ArrayList<>();
        try (var reader = reader(url, schema)) {
            for (int i = 0; i < ranges.size(); i++) {
                reader.open(MySqlSnapshotSplit.typed("decimal-" + i, "fp", ranges.get(i)));
                all.addAll(drain(reader));
            }
        }
        assertEquals(6, all.size());
        assertEquals(3, all.stream().filter(row -> row.row().getField(0)
                        .equals(new BigDecimal("1.25000000"))
                        && "same".equals(row.row().getField(1)))
                .count());
        assertEquals(2, all.stream().filter(row -> row.row().getField(0)
                        .equals(new BigDecimal("9223372036854775808.00000000")))
                .count());
        assertEquals(1, all.stream().filter(row -> row.row().getField(1) == null).count());
        assertTrue(all.stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
    }

    @Test
    void abandoningPartialScanRestartsEntireChunkWithoutSkippingEqualKeys() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (bucket BIGINT NOT NULL, payload VARCHAR(60))");
            for (int i = 0; i < 520; i++) {
                statement.execute("INSERT INTO entries VALUES (7,'same')");
            }
            statement.execute("INSERT INTO entries VALUES (9,'tail')");
        }
        var schema = new TableSchema(
                List.of(
                        new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                        new Column("payload", LogicalTypes.varchar(60))),
                List.of());
        var split = MySqlSnapshotSplit.typed(
                "replayed-chunk", "fp", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, null));
        try (var partial = reader(url, schema)) {
            partial.open(split);
            RecordsWithSplitIds<TableRecord> first = partial.fetch();
            assertEquals("replayed-chunk", first.nextSplit());
            int read = 0;
            while (first.nextRecordFromSplit() != null) read++;
            assertEquals(256, read);
            assertFalse(first.finishedSplits().contains(split.splitId()));
            RecordsWithSplitIds<TableRecord> secondPage = partial.fetch();
            assertEquals("replayed-chunk", secondPage.nextSplit());
            int secondPageRows = 0;
            while (secondPage.nextRecordFromSplit() != null) secondPageRows++;
            assertEquals(256, secondPageRows);
            assertFalse(secondPage.finishedSplits().contains(split.splitId()));
        }
        try (var restarted = reader(url, schema)) {
            restarted.open(split);
            List<TableRecord> rows = drain(restarted);
            assertEquals(521, rows.size());
            assertEquals(520, rows.stream().filter(row -> row.row().getLong(0) == 7L).count());
            assertEquals(1, rows.stream().filter(row -> row.row().getLong(0) == 9L).count());
        }
        assertEquals(0, split.outputPosition());
    }

    @Test
    void readsCollationOrderedStringRangeUsingDatabaseComparisons() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (code VARCHAR_IGNORECASE(30) NOT NULL, payload INT)");
            statement.execute("INSERT INTO entries VALUES ('a',1),('A',2),('b',3),('B',4),('c',5)");
        }
        var schema = new TableSchema(
                List.of(
                        new Column("code", LogicalTypes.varchar(30).copy(false)),
                        new Column("payload", LogicalTypes.INTEGER)),
                List.of());
        var ranges = planner(url, schema, "code", 1).splitChunks(TABLE);
        List<Integer> ids = new ArrayList<>();
        try (var reader = reader(url, schema)) {
            for (int i = 0; i < ranges.size(); i++) {
                reader.open(MySqlSnapshotSplit.typed("code-" + i, "fp", ranges.get(i)));
                for (var row : drain(reader)) ids.add(row.row().getInt(1));
            }
        }
        ids.sort(Integer::compareTo);
        assertEquals(List.of(1, 2, 3, 4, 5), ids);
    }

    @Test
    void rejectsIncorrectRangesAndFrozenOutputBeforeOpeningJdbc() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.BIGINT.copy(false))), List.of());
        var wrongColumn = MySqlSnapshotSplit.typed(
                "wrong", "fp", new ChunkRange(TABLE, "missing", LogicalTypeRoot.BIGINT, null, null));
        var wrongType = MySqlSnapshotSplit.typed(
                "type", "fp", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.DECIMAL, null, null));
        var frozen = MySqlSnapshotSplit.typed(
                        "frozen", "fp", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, null))
                .withOutput(new MySqlSnapshotOutput(offset(100), offset(200), List.of()));
        try (var reader = reader(database(), schema)) {
            assertThrows(IllegalArgumentException.class, () -> reader.open(wrongColumn));
            assertThrows(IllegalArgumentException.class, () -> reader.open(wrongType));
            assertThrows(IllegalArgumentException.class, () -> reader.open(frozen));
            assertThrows(IllegalStateException.class, reader::fetch);
        }
    }

    @Test
    void cancellationStopsTypedFetchWithoutChangingItsCheckpointProgress() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (bucket BIGINT NOT NULL)");
            statement.execute("INSERT INTO entries VALUES (1),(1),(2)");
        }
        var schema = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.BIGINT.copy(false))), List.of());
        var split = MySqlSnapshotSplit.typed(
                "cancelled", "fp", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, null));
        try (var reader = reader(url, schema)) {
            reader.open(split);
            reader.cancel();
            assertThrows(SQLException.class, reader::fetch);
            assertThrows(SQLException.class, () -> reader.open(split));
        }
        assertEquals(0, split.outputPosition());
    }

    private static SnapshotSplitReader reader(String url, TableSchema schema) {
        return new SnapshotSplitReader(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                Map.of(TABLE, schema));
    }

    private static MySqlChunkSplitter planner(String url, TableSchema schema, String column, int chunkSize) {
        return new MySqlChunkSplitter(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                Map.of(TABLE, schema),
                Map.of(TABLE, column),
                chunkSize);
    }

    private static List<TableRecord> drain(SnapshotSplitReader reader) throws SQLException {
        List<TableRecord> records = new ArrayList<>();
        boolean finished = false;
        while (!finished) {
            RecordsWithSplitIds<TableRecord> batch = reader.fetch();
            while (batch.nextSplit() != null) {
                TableRecord record;
                while ((record = batch.nextRecordFromSplit()) != null) records.add(record);
            }
            finished = !batch.finishedSplits().isEmpty();
            batch.recycle();
        }
        return records;
    }

    private static io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset offset(long pos) {
        return new io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }

    private static String database() {
        return "jdbc:h2:mem:keyless_reader_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }
}
