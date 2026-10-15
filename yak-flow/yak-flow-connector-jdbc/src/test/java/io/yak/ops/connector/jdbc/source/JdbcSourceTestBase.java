package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcRecordAndPosition;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Common source behavior with database-owned DDL and no real destination connector. */
@Tag("jdbc")
@Timeout(180)
abstract class JdbcSourceTestBase {
    protected abstract DatabaseMetadata database();

    protected abstract Map<String, String> sqlTypes();

    protected abstract String widenColumn(String table, String column);

    private JdbcConnectionOptions connectionOptions;
    private JdbcDialect dialect;
    private final List<TableId> created = new ArrayList<>();
    private TableId keys;
    private TableId notes;
    private TableId types;

    @BeforeEach
    void createFixture() throws Exception {
        DatabaseMetadata metadata = database();
        connectionOptions = new JdbcConnectionOptions(metadata.url(), metadata.username(), metadata.password());
        dialect = JdbcDialects.forUrl(metadata.url());
        String prefix = TestNames.unique("src").toUpperCase(Locale.ROOT);
        Map<String, String> ddl = sqlTypes();
        try (Connection connection = metadata.connect();
                var statement = connection.createStatement()) {
            keys = table(connection, prefix + "A");
            notes = table(connection, prefix + "B");
            types = table(connection, prefix + "C");
            create(
                    connection,
                    keys,
                    q("ID") + " " + ddl.get("integer") + " PRIMARY KEY, " + q("LABEL") + " " + ddl.get("string"));
            create(connection, notes, q("MESSAGE") + " " + ddl.get("string"));
            create(
                    connection,
                    types,
                    q("ID") + " " + ddl.get("integer") + " PRIMARY KEY, "
                            + q("AMOUNT") + " " + ddl.get("decimal") + ", " + q("LABEL") + " " + ddl.get("string")
                            + ", "
                            + q("PAYLOAD") + " " + ddl.get("binary") + ", " + q("EVENT_TS") + " " + ddl.get("timestamp")
                            + ", "
                            + q("EVENT_DATE") + " DATE, " + q("OPTIONAL_VALUE") + " " + ddl.get("string"));
            for (int id = 1; id <= 23; id++)
                statement.execute("INSERT INTO " + qualified(keys) + " VALUES (" + id + ",'item" + id + "')");
            statement.execute("INSERT INTO " + qualified(notes) + " VALUES ('one')");
            statement.execute("INSERT INTO " + qualified(notes) + " VALUES ('two')");
            try (var insert =
                    connection.prepareStatement("INSERT INTO " + qualified(types) + " VALUES (?,?,?,?,?,?,?)")) {
                insert.setLong(1, 101L);
                insert.setBigDecimal(2, new BigDecimal("123.45"));
                insert.setString(3, "typed record");
                insert.setBytes(4, new byte[] {1, 2, 3, 4});
                insert.setTimestamp(5, java.sql.Timestamp.valueOf("2026-10-09 12:34:56.123456"));
                insert.setDate(6, java.sql.Date.valueOf("2026-10-09"));
                insert.setNull(7, java.sql.Types.VARCHAR);
                insert.executeUpdate();
            }
        }
    }

    @AfterEach
    void dropFixture() throws Exception {
        SQLException failure = null;
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            for (TableId table : created.reversed()) {
                try {
                    statement.execute("DROP TABLE " + qualified(table));
                } catch (SQLException cleanup) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure != null) throw failure;
    }

    @Test
    void catalogReadsTablesKeysAndLogicalPrecision() throws Exception {
        try (var catalog = JdbcCatalogFactory.create(connectionOptions)) {
            assertTrue(!catalog.listDatabases().isEmpty());
            var tables = catalog.listTables(keys.catalog(), keys.schema());
            assertTrue(tables.stream().anyMatch(value -> value.table().equals(keys.table())));
            assertTrue(tables.stream().anyMatch(value -> value.table().equals(types.table())));
            assertEquals(List.of("ID"), catalog.getTable(keys).primaryKeys());
            assertEquals(
                    "DECIMAL(18, 2)",
                    catalog.getTable(types).columns().get(1).dataType().asSerializableString());
            assertTrue(!catalog.tableExists(new TableId(keys.catalog(), keys.schema(), "NO_SUCH_TABLE")));
        }
    }

    @Test
    void readsMultipleTablesThroughRuntimeWithoutLossOrDuplication() throws Exception {
        Configuration options = options();
        assertTrue(new JdbcSplitPlanner(connectionOptions, dialect, options)
                        .plan(keys, 0)
                        .size()
                > 1);
        var received = new CopyOnWriteArrayList<TableRecord>();
        Sink<TableRecord> collector = context -> new SinkWriter<>() {
            public void write(TableRecord record, Context metadata) {
                received.add(record);
            }

            public void flush(boolean endOfInput) {}

            public void close() {}
        };
        var source = new SourceTransformation<>(
                "jdbc-source",
                new JdbcSource(connectionOptions, List.of(keys, notes, types), options),
                TableRecord.class,
                3);
        var sink = new SinkTransformation<>(source, "collect", collector, 1);
        JobClient job = new EmbeddedPipelineExecutor()
                .execute(new StreamGraphGenerator(sink, options).generate(), options)
                .get(10, TimeUnit.SECONDS);
        try {
            job.getJobExecutionResult().get(60, TimeUnit.SECONDS);
            assertEquals(JobStatus.FINISHED, job.getJobStatus().get(10, TimeUnit.SECONDS));
            assertEquals(26, received.size());
            Map<TableId, Long> counts =
                    received.stream().collect(Collectors.groupingBy(TableRecord::tableId, Collectors.counting()));
            assertEquals(23L, counts.get(keys));
            assertEquals(2L, counts.get(notes));
            assertEquals(1L, counts.get(types));
            assertEquals(
                    java.util.stream.LongStream.rangeClosed(1, 23).boxed().collect(Collectors.toSet()),
                    received.stream()
                            .filter(row -> row.tableId().equals(keys))
                            .map(row -> ((Number) row.row().getField(0)).longValue())
                            .collect(Collectors.toSet()));
            for (TableRecord record : received) {
                assertEquals(RowKind.INSERT, record.rowKind());
                if (record.tableId().equals(keys))
                    assertEquals(
                            "item" + ((Number) record.row().getField(0)).longValue(),
                            record.row().getString(1));
            }
        } finally {
            if (!job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState())
                job.cancel().get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void splitReaderPreservesNullBinaryDecimalAndTimePrecision() throws Exception {
        List<TableRecord> records = read(types);
        assertEquals(1, records.size());
        var row = records.getFirst().row();
        assertEquals(new BigDecimal("123.45"), row.getDecimal(1, 18, 2));
        assertEquals("typed record", row.getString(2));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, row.getBinary(3));
        assertEquals(LocalDateTime.parse("2026-10-09T12:34:56.123456"), row.getTimestamp(4, 6));
        if (database().url().startsWith("jdbc:oracle:"))
            assertEquals(LocalDate.of(2026, 10, 9).atStartOfDay(), row.getTimestamp(5, 0));
        else assertEquals(LocalDate.of(2026, 10, 9), row.getDate(5));
        assertTrue(row.isNullAt(6));
    }

    @Test
    void emptyTableCompletesWithoutRecords() throws Exception {
        database().execute("DELETE FROM " + qualified(keys));
        assertEquals(List.of(), read(keys));
    }

    @Test
    void staleSplitRejectsChangedSchemaBeforeEmittingRecords() throws Exception {
        var splits = new JdbcSplitPlanner(connectionOptions, dialect, options()).plan(keys, 0);
        database().execute(widenColumn(qualified(keys), q("LABEL")));
        try (var reader = new JdbcSourceSplitReader(connectionOptions, dialect, options())) {
            reader.addSplits(List.of(splits.getFirst()));
            assertTrue(
                    assertThrows(SQLException.class, reader::fetch).getMessage().contains("schema fingerprint"));
        }
    }

    private List<TableRecord> read(TableId table) throws Exception {
        var splits = new JdbcSplitPlanner(connectionOptions, dialect, options()).plan(table, 0);
        List<TableRecord> result = new ArrayList<>();
        Set<String> completed = new java.util.HashSet<>();
        try (var reader = new JdbcSourceSplitReader(connectionOptions, dialect, options())) {
            reader.addSplits(splits);
            for (int attempt = 0; completed.size() < splits.size() && attempt < 1000; attempt++) {
                var batch = reader.fetch();
                while (batch.nextSplit() != null) {
                    JdbcRecordAndPosition record;
                    while ((record = batch.nextRecordFromSplit()) != null) result.add(record.record());
                }
                completed.addAll(batch.finishedSplits());
            }
        }
        assertEquals(splits.size(), completed.size(), "All assigned splits must finish");
        return result;
    }

    private Configuration options() {
        return new Configuration()
                .set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 5)
                .set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 6)
                .set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2)
                .set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 3);
    }

    private TableId table(Connection connection, String name) throws SQLException {
        boolean mysql = database().url().startsWith("jdbc:mysql:");
        return new TableId(mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), name);
    }

    private void create(Connection connection, TableId table, String fields) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + qualified(table) + " (" + fields + ")");
        }
        created.add(table);
    }

    private String q(String field) {
        return dialect.quoteIdentifier(field);
    }

    private String qualified(TableId table) {
        return dialect.qualifiedTable(table);
    }
}
