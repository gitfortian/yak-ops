package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.JdbcAssertions;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests actual writers against one database without a JDBC source or Boot application. */
@Tag("jdbc")
@Timeout(120)
abstract class JdbcSinkTestBase {
    protected abstract DatabaseMetadata database();

    protected abstract String fields();

    private TableId table;
    private JdbcDialect dialect;
    private boolean created;
    private final TableSchema schema = new TableSchema(
            List.of(
                    new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                    new Column("LABEL", LogicalTypes.varchar(48)),
                    new Column("AMOUNT", LogicalTypes.decimal(18, 2)),
                    new Column("PAYLOAD", LogicalTypes.BINARY)),
            List.of("ID"));
    private static final SinkWriter.Context RECORD = new SinkWriter.Context() {
        public Long timestamp() {
            return null;
        }

        public long currentWatermark() {
            return Long.MIN_VALUE;
        }
    };

    @BeforeEach
    void createTableIndependentlyOfProductionDdl() throws Exception {
        DatabaseMetadata db = database();
        dialect = JdbcDialects.forUrl(db.url());
        try (Connection connection = db.connect();
                var statement = connection.createStatement()) {
            boolean mysql = db.url().startsWith("jdbc:mysql:");
            table = new TableId(
                    mysql ? connection.getCatalog() : null,
                    mysql ? null : connection.getSchema(),
                    TestNames.unique("sink").toUpperCase(Locale.ROOT));
            statement.execute("CREATE TABLE " + dialect.qualifiedTable(table) + " (" + fields() + ")");
            created = true;
        }
    }

    @AfterEach
    void dropTable() throws Exception {
        if (created) database().execute("DROP TABLE " + dialect.qualifiedTable(table));
    }

    @Test
    void appendFlushPreservesNullDecimalBinaryAndUnicode() throws Exception {
        try (JdbcWriter writer = writer(JdbcWriteMode.APPEND)) {
            writer.write(
                    record(RowKind.INSERT, 1L, "中文|line\n", new BigDecimal("12.30"), new byte[] {0, 1, -1}), RECORD);
            writer.write(record(RowKind.INSERT, 2L, null, null, null), RECORD);
            writer.flush(true);
        }
        try (Connection connection = database().connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT * FROM " + dialect.qualifiedTable(table) + " ORDER BY "
                        + dialect.quoteIdentifier("ID"))) {
            assertTrue(rows.next());
            assertEquals(new BigDecimal("1"), rows.getBigDecimal(1));
            assertEquals("中文|line\n", rows.getString(2));
            assertEquals(new BigDecimal("12.30"), rows.getBigDecimal(3));
            assertArrayEquals(new byte[] {0, 1, -1}, rows.getBytes(4));
            assertTrue(rows.next());
            assertEquals(new BigDecimal("2"), rows.getBigDecimal(1));
            assertEquals(null, rows.getString(2));
            assertEquals(null, rows.getBigDecimal(3));
            assertEquals(null, rows.getBytes(4));
            assertTrue(!rows.next());
        }
    }

    @Test
    void upsertUpdateDeleteAndReplayHaveExplicitFinalState() throws Exception {
        try (JdbcWriter writer = writer(JdbcWriteMode.UPSERT)) {
            writer.write(record(RowKind.INSERT, 1, "before", null, null), RECORD);
            writer.write(record(RowKind.INSERT, 2, "removed", null, null), RECORD);
            writer.flush(false);
            writer.write(record(RowKind.UPDATE_BEFORE, 1, "before", null, null), RECORD);
            writer.write(record(RowKind.UPDATE_AFTER, 1, "after", null, null), RECORD);
            writer.write(record(RowKind.DELETE, 2, "removed", null, null), RECORD);
            writer.flush(false);
            writer.write(record(RowKind.INSERT, 1, "after", null, null), RECORD);
            writer.write(record(RowKind.DELETE, 2, "removed", null, null), RECORD);
            writer.flush(true);
        }
        try (Connection connection = database().connect()) {
            JdbcAssertions.assertRows(
                    List.of(Arrays.asList(new BigDecimal("1"), "after")),
                    JdbcAssertions.query(
                            connection,
                            "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("LABEL")
                                    + " FROM " + dialect.qualifiedTable(table)));
        }
    }

    @Test
    void duplicateAppendIsNotSilentlyAccepted() throws Exception {
        try (JdbcWriter first = writer(JdbcWriteMode.APPEND)) {
            first.write(record(RowKind.INSERT, 1, "one", null, null), RECORD);
            first.flush(true);
        }
        JdbcWriter duplicate = writer(JdbcWriteMode.APPEND);
        try {
            assertThrows(Exception.class, () -> {
                duplicate.write(record(RowKind.INSERT, 1, "duplicate", null, null), RECORD);
                duplicate.flush(true);
            });
        } finally {
            duplicate.close();
        }
        try (var connection = database().connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + dialect.qualifiedTable(table))) {
            assertTrue(rows.next());
            assertEquals(1, rows.getLong(1));
        }
    }

    private JdbcWriter writer(JdbcWriteMode mode) throws Exception {
        DatabaseMetadata db = database();
        return JdbcSink.builder()
                .withConnectionOptions(new JdbcConnectionOptions(db.url(), db.username(), db.password()))
                .withTablePlans(List.of(new JdbcTableWritePlan(table, table, schema, mode)))
                .withBatchFlushPolicy(new BatchFlushPolicy(32, Duration.ZERO))
                .build()
                .createWriter(new WriterInitContext() {
                    public TaskInfo getTaskInfo() {
                        throw new UnsupportedOperationException("Writer fixture has no task graph");
                    }

                    public Configuration getConfiguration() {
                        return new Configuration();
                    }
                });
    }

    private TableRecord record(RowKind kind, long id, String label, BigDecimal amount, byte[] payload) {
        return new TableRecord(table, kind, GenericRowData.of(BigDecimal.valueOf(id), label, amount, payload));
    }
}
