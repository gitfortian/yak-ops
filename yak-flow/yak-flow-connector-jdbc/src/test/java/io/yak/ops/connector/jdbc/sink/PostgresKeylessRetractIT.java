package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.PostgresDatabase;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real PostgreSQL tuple-level keyless RETRACT and durable transaction replay acceptance. */
@Tag("jdbc")
@Tag("postgres")
@Timeout(120)
class PostgresKeylessRetractIT {
    @RegisterExtension
    static final PostgresDatabase DATABASE = new PostgresDatabase();

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(14, 2)),
                    new Column("payload", LogicalTypes.varbinary(12))),
            List.of());

    private final PostgresJdbcDialect dialect = new PostgresJdbcDialect();

    @Test
    void duplicateRowsAndNullableBinaryBeforeImagesRetractExactlyOne() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                TableRecord before = row(table, RowKind.UPDATE_BEFORE, 2, null, null, "update-2", 0, 2);
                TableRecord after = row(table, RowKind.UPDATE_AFTER, 3, "12.00", new byte[] {5},
                        "update-2", 1, 2);
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.INSERT, 1, "8.50", new byte[] {0, 1}, "insert-a", 0, 1));
                    writer.add(row(table, RowKind.INSERT, 1, "8.50", new byte[] {0, 1}, "insert-b", 0, 1));
                    writer.add(row(table, RowKind.INSERT, 2, null, null, "insert-c", 0, 1));
                    writer.add(row(table, RowKind.DELETE, 1, "8.50", new byte[] {0, 1}, "remove-a", 0, 1));
                    writer.add(before);
                    writer.add(after);
                    writer.flush();
                }
                assertEquals(1, count(connection, table, 1));
                assertEquals(0, count(connection, table, 2));
                assertEquals(1, count(connection, table, 3));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void replayAfterCommitSkipsCompleteUpdateAndPhysicalDuplicates() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                var first = row(table, RowKind.INSERT, 7, null, null, "same-value-first", 0, 1);
                var second = row(table, RowKind.INSERT, 7, null, null, "same-value-second", 0, 1);
                var before = row(table, RowKind.UPDATE_BEFORE, 7, null, null, "change", 0, 2);
                var after = row(table, RowKind.UPDATE_AFTER, 8, "3.00", null, "change", 1, 2);
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(first);
                    writer.add(second);
                    writer.add(before);
                    writer.add(after);
                    writer.flush();
                }
                assertEquals(1, count(connection, table, 7));
                assertEquals(1, count(connection, table, 8));
                // A target commit preceded Source checkpoint persistence; source replays all events.
                try (JdbcOutputFormat restored = writer(table)) {
                    restored.add(first);
                    restored.add(second);
                    restored.add(before);
                    restored.add(after);
                    restored.flush();
                }
                assertEquals(1, count(connection, table, 7));
                assertEquals(1, count(connection, table, 8));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void rollbackDiscardsReceiptsAndUniqueTargetsAreRejected() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                TableRecord one = row(table, RowKind.INSERT, 5, null, null, "rollback", 0, 1);
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(one);
                    writer.add(row(table, RowKind.DELETE, 6, null, null, "missing", 0, 1));
                    assertThrows(SQLException.class, writer::flush);
                }
                assertEquals(0, count(connection, table, 5));
                try (JdbcOutputFormat retried = writer(table)) {
                    retried.add(one);
                    retried.flush();
                }
                assertEquals(1, count(connection, table, 5));
            } finally {
                drop(connection, table);
            }
            TableId unsafe = table(connection);
            create(connection, unsafe, ", UNIQUE (bucket)");
            try {
                assertThrows(SQLException.class, () -> writer(unsafe));
            } finally {
                drop(connection, unsafe);
            }
        }
    }

    private JdbcOutputFormat writer(TableId table) throws SQLException {
        return new JdbcOutputFormat(
                () -> DATABASE.metadata().connect(), dialect,
                List.of(new JdbcTableWritePlan(table, table, SCHEMA, JdbcWriteMode.RETRACT)));
    }

    private TableRecord row(
            TableId table, RowKind kind, long bucket, String amount, byte[] payload,
            String event, int image, int images) {
        return new TableRecord(
                table, kind,
                GenericRowData.of(bucket, amount == null ? null : new BigDecimal(amount), payload),
                new ChangeEventId("postgres-retract-" + table.table(), event, image, images));
    }

    private TableId table(Connection connection) throws SQLException {
        return new TableId(null, connection.getSchema(), TestNames.unique("pg_retract").toLowerCase(Locale.ROOT));
    }

    private void create(Connection connection, TableId table, String constraint) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS yak_flow_cdc_receipts "
                    + "(receipt_key BYTEA NOT NULL PRIMARY KEY)");
            statement.execute("CREATE TABLE " + dialect.qualifiedTable(table)
                    + " (bucket BIGINT NOT NULL, amount NUMERIC(14,2), payload BYTEA"
                    + constraint + ")");
        }
    }

    private void drop(Connection connection, TableId table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(table));
        }
    }

    private long count(Connection connection, TableId table, long value) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + dialect.qualifiedTable(table) + " WHERE bucket = ?")) {
            query.setLong(1, value);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
