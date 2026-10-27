package io.yak.ops.connector.cdc.mysql.source.assigners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises typed MySQL-ordered chunk planning without changing the legacy checkpoint format. */
class MySqlTypedChunkSplitterTest {
    private static final TableId CODES = new TableId(null, null, "codes");
    private static final TableId PRICES = new TableId(null, null, "prices");

    @Test
    void retainsAllDuplicateStringKeysWithinOneRangeAndCanResumeDiscovery() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE codes (id INT NOT NULL, code VARCHAR(40) NOT NULL)");
            statement.execute(
                    "INSERT INTO codes VALUES (1,'A'),(2,'A'),(3,'A'),(4,'B'),(5,'B'),(6,'C'),(7,'D')");
        }
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("code", LogicalTypes.varchar(40).copy(false))),
                List.of());
        var splitter = splitter(url, Map.of(CODES, schema), Map.of(CODES, "code"), 2);
        List<ChunkRange> ranges = splitter.splitChunks(CODES);

        assertEquals(3, ranges.size());
        assertEquals("B", ranges.getFirst().end());
        assertEquals("B", ranges.get(1).start());
        assertEquals("C", ranges.get(1).end());
        assertEquals("C", ranges.getLast().start());
        assertTrue(ranges.getLast().last());
        assertEquals(ranges.get(1), splitter.nextChunk(CODES, ranges.getFirst().end()));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), scannedIds(url, ranges));
    }

    @Test
    void usesDatabaseCollationInsteadOfJavaStringOrdering() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE codes (id INT NOT NULL, code VARCHAR_IGNORECASE(40) NOT NULL)");
            statement.execute(
                    "INSERT INTO codes VALUES (1,'a'),(2,'A'),(3,'b'),(4,'B'),(5,'c')");
        }
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("code", LogicalTypes.varchar(40).copy(false))),
                List.of());
        var ranges = splitter(url, Map.of(CODES, schema), Map.of(CODES, "code"), 1).splitChunks(CODES);
        assertEquals(List.of(1, 2, 3, 4, 5), scannedIds(url, ranges));
        assertEquals(3, ranges.size());
    }

    @Test
    void preservesWideDecimalPrecisionAndSupportsExactDiscoveryResume() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE prices (id INT NOT NULL, price DECIMAL(30,8) NOT NULL)");
            statement.execute("INSERT INTO prices VALUES "
                    + "(1,0.00000001),(2,1.23000000),"
                    + "(3,9223372036854775808.00000000),(4,9223372036854775808.00000000)");
        }
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("price", LogicalTypes.decimal(30, 8).copy(false))),
                List.of("id"));
        var splitter = splitter(url, Map.of(PRICES, schema), Map.of(PRICES, "price"), 2);
        List<ChunkRange> ranges = splitter.splitChunks(PRICES);
        assertEquals(2, ranges.size());
        assertEquals(new BigDecimal("9223372036854775808.00000000"), ranges.getFirst().end());
        assertEquals(ranges.getFirst().end(), ranges.getLast().start());
        assertTrue(ranges.getLast().last());
        assertEquals(ranges.getLast(), splitter.nextChunk(PRICES, ranges.getFirst().end()));
        assertEquals(List.of(1, 2, 3, 4), scannedIds(url, ranges));
    }

    @Test
    void supportsEmptyTableAndOneValueWithoutInventingBoundaries() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE codes (id INT NOT NULL, code VARCHAR(40) NOT NULL)");
        }
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("code", LogicalTypes.varchar(40).copy(false))),
                List.of());
        var splitter = splitter(url, Map.of(CODES, schema), Map.of(CODES, "code"), 1);
        assertEquals(List.of(new ChunkRange(CODES, "code",
                io.yak.ops.core.types.LogicalTypeRoot.VARCHAR, null, null)), splitter.splitChunks(CODES));
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO codes VALUES (1,'same'),(2,'same'),(3,'same')");
        }
        var ranges = splitter.splitChunks(CODES);
        assertEquals(1, ranges.size());
        assertEquals(List.of(1, 2, 3), scannedIds(url, ranges));
    }

    @Test
    void rejectsUnsupportedAndMismatchedBoundariesBeforeJdbcIsOpened() throws Exception {
        var boolSchema = new TableSchema(
                List.of(new Column("active", LogicalTypes.BOOLEAN.copy(false))), List.of());
        var splitter = splitter(
                "jdbc:h2:mem:unused_" + UUID.randomUUID() + ";MODE=MySQL",
                Map.of(CODES, boolSchema),
                Map.of(CODES, "active"),
                2);
        assertThrows(IllegalArgumentException.class, () -> splitter.nextChunk(CODES, null));
        var varchar = new TableSchema(
                List.of(new Column("code", LogicalTypes.varchar(20).copy(false))), List.of());
        var mismatch = splitter(
                "jdbc:h2:mem:unused_" + UUID.randomUUID() + ";MODE=MySQL",
                Map.of(CODES, varchar),
                Map.of(CODES, "code"),
                2);
        assertThrows(IllegalArgumentException.class, () -> mismatch.nextChunk(CODES, 10L));
        assertThrows(IllegalArgumentException.class, () -> new ChunkRange(
                CODES, "code", io.yak.ops.core.types.LogicalTypeRoot.BIGINT, 10L, 9L));
    }

    private static MySqlChunkSplitter splitter(
            String url, Map<TableId, TableSchema> schemas, Map<TableId, String> columns, int chunkSize) {
        return new MySqlChunkSplitter(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                schemas,
                columns,
                chunkSize);
    }

    /** Verifies a bag of rows, not only total counts; every source row belongs to exactly one range. */
    private static List<Integer> scannedIds(String url, List<ChunkRange> ranges) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (var connection = DriverManager.getConnection(url)) {
            for (ChunkRange range : ranges) {
                String col = "`" + range.column() + "`";
                List<String> predicates = new ArrayList<>();
                if (range.start() != null) predicates.add(col + " >= ?");
                if (range.end() != null) predicates.add(col + " < ?");
                String sql = "SELECT id FROM `" + range.tableId().table() + "`"
                        + (predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates));
                try (var statement = connection.prepareStatement(sql)) {
                    int position = 1;
                    if (range.start() != null) statement.setObject(position++, range.start());
                    if (range.end() != null) statement.setObject(position, range.end());
                    try (var records = statement.executeQuery()) {
                        while (records.next()) ids.add(records.getInt(1));
                    }
                }
            }
        }
        assertEquals(ids.size(), new HashSet<>(ids).size(), "A row must not belong to two chunks");
        assertFalse(ranges.isEmpty());
        ids.sort(Integer::compareTo);
        return ids;
    }

    private static String database() {
        return "jdbc:h2:mem:typed_chunks_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }
}
