package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.OracleDatabase;
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

/** Real Oracle NUMBER-only keyless RETRACT; verifies ROWID and DECODE. */
@Tag("jdbc")
@Tag("oracle")
@Timeout(180)
class OracleKeylessRetractIT {
    @RegisterExtension
    static final OracleDatabase DATABASE = new OracleDatabase();

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("BUCKET", LogicalTypes.BIGINT.copy(false)),
                    new Column("AMOUNT", LogicalTypes.decimal(14, 2))),
            List.of());

    private final OracleJdbcDialect dialect = new OracleJdbcDialect();

    @Test
    void nullableBeforeImageDeletesExactlyOneDuplicateAndUpdatesAtomically() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.INSERT, 1, null, "first", 0, 1));
                    writer.add(row(table, RowKind.INSERT, 1, null, "second", 0, 1));
                    writer.add(row(table, RowKind.DELETE, 1, null, "remove", 0, 1));
                    writer.add(row(table, RowKind.UPDATE_BEFORE, 1, null, "update", 0, 2));
                    writer.add(row(table, RowKind.UPDATE_AFTER, 2, "12.00", "update", 1, 2));
                    writer.flush();
                }
                assertEquals(0, count(connection, table, 1));
                assertEquals(1, count(connection, table, 2));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void independentMutationsPreserveDuplicateMultiplicity() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                TableRecord first = row(table, RowKind.INSERT, 4, "5.00", "insert-a", 0, 1);
                TableRecord duplicate = row(table, RowKind.INSERT, 4, "5.00", "insert-b", 0, 1);
                TableRecord removed = row(table, RowKind.DELETE, 4, "5.00", "remove", 0, 1);
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(first);
                    writer.add(duplicate);
                    writer.add(removed);
                    writer.flush();
                }
                assertEquals(1, count(connection, table, 4));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void failedDeleteRollsBackAndNewAttemptCanApplyInsert() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                TableRecord original = row(table, RowKind.INSERT, 11, null, "retry", 0, 1);
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(original);
                    writer.add(row(table, RowKind.DELETE, 12, null, "missing", 0, 1));
                    assertThrows(SQLException.class, writer::flush);
                }
                assertEquals(0, count(connection, table, 11));
                try (JdbcOutputFormat restored = writer(table)) {
                    restored.add(original);
                    restored.flush();
                }
                assertEquals(1, count(connection, table, 11));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void uniqueIndexesAndUnsupportedRawFullRowComparisonsFailClosed() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId unique = table(connection);
            create(connection, unique, ", CONSTRAINT " + dialect.quoteIdentifier("U_" + unique.table())
                    + " UNIQUE (BUCKET)");
            try {
                assertThrows(SQLException.class, () -> writer(unique));
            } finally {
                drop(connection, unique);
            }

            TableId raw = table(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(raw) + " "
                        + "(BUCKET NUMBER(19) NOT NULL, PAYLOAD RAW(12))");
            }
            try {
                TableSchema rawSchema = new TableSchema(
                        List.of(
                                new Column("BUCKET", LogicalTypes.BIGINT.copy(false)),
                                new Column("PAYLOAD", LogicalTypes.varbinary(12))),
                        List.of());
                assertThrows(SQLException.class, () -> new JdbcOutputFormat(
                        () -> DATABASE.metadata().connect(), dialect,
                        List.of(new JdbcTableWritePlan(raw, raw, rawSchema, JdbcWriteMode.RETRACT))));
            } finally {
                drop(connection, raw);
            }
        }
    }

    private JdbcOutputFormat writer(TableId table) throws SQLException {
        return new JdbcOutputFormat(
                () -> DATABASE.metadata().connect(), dialect,
                List.of(new JdbcTableWritePlan(table, table, SCHEMA, JdbcWriteMode.RETRACT)));
    }

    private TableRecord row(
            TableId table, RowKind kind, long bucket, String amount, String event, int image, int count) {
        return new TableRecord(
                table, kind,
                GenericRowData.of(bucket, amount == null ? null : new BigDecimal(amount)),
                new ChangeEventId("oracle-retract-" + table.table(), event, image, count));
    }

    private TableId table(Connection connection) throws SQLException {
        return new TableId(null, connection.getSchema(), TestNames.unique("oracle_retract").toUpperCase(Locale.ROOT));
    }

    private void create(Connection connection, TableId table, String constraint) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + dialect.qualifiedTable(table)
                    + " (BUCKET NUMBER(19) NOT NULL, AMOUNT NUMBER(14,2)" + constraint + ")");
        }
    }

    private void drop(Connection connection, TableId table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + dialect.qualifiedTable(table) + " PURGE");
        }
    }

    private long count(Connection connection, TableId table, long bucket) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + dialect.qualifiedTable(table) + " WHERE BUCKET = ?")) {
            query.setLong(1, bucket);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
