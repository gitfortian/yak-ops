package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Exercises the actual MySQL JDBC writer; no H2 approximation for DELETE LIMIT 1. */
@Tag("jdbc")
@Tag("mysql")
@Timeout(120)
class MySqlKeylessRetractIT {
    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(14, 2)),
                    new Column("payload", LogicalTypes.varbinary(12))),
            List.of());
    private final MySqlJdbcDialect dialect = new MySqlJdbcDialect();

    @Test
    void removesExactlyOneDuplicateAndChangesNullableBinaryFullRow() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.INSERT, 1, "8.50", new byte[] {0, 1}));
                    writer.add(row(table, RowKind.INSERT, 1, "8.50", new byte[] {0, 1}));
                    writer.add(row(table, RowKind.INSERT, 2, null, null));
                    writer.flush();

                    writer.add(row(table, RowKind.DELETE, 1, "8.50", new byte[] {0, 1}));
                    writer.add(row(table, RowKind.UPDATE_BEFORE, 2, null, null)
                            .withChangeEventId(new ChangeEventId("source", "event-1", 0, 2)));
                    writer.add(row(table, RowKind.UPDATE_AFTER, 3, "12.00", new byte[] {5})
                            .withChangeEventId(new ChangeEventId("source", "event-1", 1, 2)));
                    writer.flush();
                }
                assertEquals(1, count(connection, table, 1));
                assertEquals(0, count(connection, table, 2));
                assertEquals(1, count(connection, table, 3));
                assertEquals(2, countAll(connection, table));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void missingBeforeImageAndMissingDeleteRollbackEntireFlush() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                try (JdbcOutputFormat writer = writer(table)) {
                    assertThrows(IllegalArgumentException.class,
                            () -> writer.add(row(table, RowKind.UPDATE_AFTER, 4, "1.00", null)));
                }
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.UPDATE_BEFORE, 4, "1.00", null)
                            .withChangeEventId(new ChangeEventId("source", "incomplete", 0, 2)));
                    assertThrows(IllegalStateException.class, writer::flush);
                }
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.INSERT, 5, "2.00", null));
                    writer.add(row(table, RowKind.DELETE, 6, "9.00", null));
                    assertThrows(SQLException.class, writer::flush);
                }
                assertEquals(0, countAll(connection, table));
            } finally {
                drop(connection, table);
            }
        }
    }

    @Test
    void refusesUniqueIndexesAndNarrowerPhysicalDecimalTypes() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, ", UNIQUE KEY only_one (bucket)");
            try {
                assertThrows(SQLException.class, () -> writer(table));
                assertEquals(0, countAll(connection, table));
            } finally {
                drop(connection, table);
            }
            TableId narrow = table(connection);
            create(connection, narrow, "");
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + dialect.qualifiedTable(narrow)
                            + " MODIFY amount DECIMAL(10,2) NULL");
                }
                assertThrows(SQLException.class, () -> writer(narrow));
                assertEquals(0, countAll(connection, narrow));
            } finally {
                drop(connection, narrow);
            }
        }
    }

    @Test
    void updateIdentityMustBeAtomicAndConsistent() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId table = table(connection);
            create(connection, table, "");
            try {
                try (JdbcOutputFormat writer = writer(table)) {
                    writer.add(row(table, RowKind.UPDATE_BEFORE, 1, null, null)
                            .withChangeEventId(new ChangeEventId("source", "first", 0, 2)));
                    assertThrows(IllegalArgumentException.class,
                            () -> writer.add(row(table, RowKind.UPDATE_AFTER, 2, null, null)
                                    .withChangeEventId(new ChangeEventId("source", "other", 1, 2))));
                }
                assertEquals(0, countAll(connection, table));
            } finally {
                drop(connection, table);
            }
        }
    }

    private JdbcOutputFormat writer(TableId table) throws SQLException {
        return new JdbcOutputFormat(
                () -> DATABASE.metadata().connect(),
                dialect,
                List.of(new JdbcTableWritePlan(table, table, SCHEMA, JdbcWriteMode.RETRACT)));
    }

    private TableId table(Connection connection) throws SQLException {
        return new TableId(connection.getCatalog(), null, TestNames.unique("keyless_retract"));
    }

    private void create(Connection connection, TableId table, String constraints) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `yak_flow_cdc_receipts` "
                    + "(`receipt_key` BINARY(32) NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE " + dialect.qualifiedTable(table)
                    + " (bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL, "
                    + "payload VARBINARY(12) NULL" + constraints + ") ENGINE=InnoDB");
        }
    }

    private void drop(Connection connection, TableId table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(table));
        }
    }

    private TableRecord row(TableId table, RowKind kind, long bucket, String amount, byte[] payload) {
        TableRecord record = new TableRecord(table, kind, GenericRowData.of(
                bucket, amount == null ? null : new BigDecimal(amount), payload));
        if (kind == RowKind.INSERT || kind == RowKind.DELETE) {
            return record.withChangeEventId(new ChangeEventId(
                    "source-" + table.table(), UUID.randomUUID().toString(), 0, 1));
        }
        return record;
    }

    private long count(Connection connection, TableId table, long bucket) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + dialect.qualifiedTable(table) + " WHERE bucket = ?")) {
            statement.setLong(1, bucket);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private long countAll(Connection connection, TableId table) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + dialect.qualifiedTable(table))) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
