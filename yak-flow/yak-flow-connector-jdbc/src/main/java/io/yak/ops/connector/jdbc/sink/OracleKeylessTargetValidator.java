package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Verifies conservative Oracle physical-column and heap contracts for full-row RETRACT.
 *
 * <p>Only NUMBER columns are admitted on the target while vendor-specific RAW
 * full-row matching remains unverified. A verified exact-row comparator must never
 * silently accept a different Oracle physical representation.
 */
public final class OracleKeylessTargetValidator {
    private OracleKeylessTargetValidator() {}

    public static void validate(Connection connection, JdbcTableWritePlan plan) throws SQLException {
        JdbcKeylessRetractionContract.validate(plan.sourceSchema(), plan.schema());
        String owner = plan.targetTable().schema() == null
                ? connection.getSchema()
                : plan.targetTable().schema();
        if (owner == null || owner.isBlank()) {
            throw new SQLException("Oracle RETRACT requires a resolved target owner");
        }
        String target = plan.targetTable().table();
        validateHeap(connection, owner, target);
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM ALL_INDEXES WHERE TABLE_OWNER = ? AND TABLE_NAME = ? "
                                + "AND UNIQUENESS = 'UNIQUE'",
                        owner,
                        target)
                != 0) {
            throw new SQLException("Oracle RETRACT target cannot have unique indexes");
        }
        validateSideEffects(connection, owner, target);
        validateColumns(connection, owner, target, plan.schema());

    }

    private static void validateHeap(Connection connection, String owner, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT TEMPORARY, IOT_TYPE, PARTITIONED FROM ALL_TABLES " + "WHERE OWNER = ? AND TABLE_NAME = ?")) {
            query.setString(1, owner);
            query.setString(2, table);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()
                        || !"N".equals(rows.getString(1))
                        || rows.getString(2) != null
                        || !"NO".equals(rows.getString(3))
                        || rows.next()) {
                    throw new SQLException("Oracle RETRACT requires a permanent nonpartitioned heap: " + table);
                }
            }
        }
    }

    private static void validateSideEffects(Connection connection, String owner, String table) throws SQLException {
        if (count(
                                connection,
                                "SELECT COUNT(*) FROM ALL_TRIGGERS WHERE TABLE_OWNER = ? AND TABLE_NAME = ?",
                                owner,
                                table)
                        != 0
                || count(
                                connection,
                                "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE OWNER = ? AND TABLE_NAME = ? "
                                        + "AND CONSTRAINT_TYPE = 'R'",
                                owner,
                                table)
                        != 0) {
            throw new SQLException("Oracle RETRACT target has triggers or outgoing foreign keys");
        }
        try (PreparedStatement query =
                connection.prepareStatement("SELECT COUNT(*) FROM ALL_CONSTRAINTS child JOIN ALL_CONSTRAINTS parent "
                        + "ON child.R_OWNER = parent.OWNER AND child.R_CONSTRAINT_NAME = parent.CONSTRAINT_NAME "
                        + "WHERE child.CONSTRAINT_TYPE = 'R' AND parent.OWNER = ? AND parent.TABLE_NAME = ?")) {
            query.setString(1, owner);
            query.setString(2, table);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                if (rows.getLong(1) != 0) {
                    throw new SQLException("Oracle RETRACT target is referenced by a foreign key");
                }
            }
        }
    }

    private static void validateColumns(
            Connection connection, String owner, String table, TableSchema schema)
            throws SQLException {
        try (PreparedStatement query =
                connection.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE, DATA_LENGTH, "
                        + "NULLABLE, VIRTUAL_COLUMN, IDENTITY_COLUMN FROM ALL_TAB_COLS "
                        + "WHERE OWNER = ? AND TABLE_NAME = ? AND HIDDEN_COLUMN = 'NO' ORDER BY COLUMN_ID")) {
            query.setString(1, owner);
            query.setString(2, table);
            try (ResultSet rows = query.executeQuery()) {
                int expected = schema.columnCount();
                for (int index = 0; index < expected; index++) {
                    if (!rows.next()) throw new SQLException("Oracle RETRACT physical column projection is incomplete");
                    String name = rows.getString(1);
                    String nativeType = rows.getString(2).toUpperCase(Locale.ROOT);
                    int precision = rows.getInt(3);
                    int scale = rows.getInt(4);
                    int length = rows.getInt(5);
                    boolean nullable = "Y".equals(rows.getString(6));
                    if (!"NO".equals(rows.getString(7)) || !"NO".equals(rows.getString(8))) {
                        throw new SQLException("Oracle RETRACT excludes identity and virtual columns");
                    }
                    Column column = schema.column(index);
                    if (!column.name().equals(name)
                            || column.nullable() != nullable
                            || !matches(column, nativeType, precision, scale)) {
                        throw new SQLException("Unsupported Oracle RETRACT physical column: " + name);
                    }
                }
                if (rows.next()) {
                    throw new SQLException("Oracle RETRACT requires every physical column in the before-image");
                }
            }
        }
    }

    private static boolean matches(Column column, String nativeType, int precision, int scale) {
        if (!"NUMBER".equals(nativeType)
                || scale != 0 && column.dataType().getTypeRoot() != io.yak.ops.core.types.LogicalTypeRoot.DECIMAL) {
            return false;
        }
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> precision == 1 && scale == 0;
            case TINYINT -> precision == 3 && scale == 0;
            case SMALLINT -> precision == 5 && scale == 0;
            case INTEGER -> precision == 10 && scale == 0;
            case BIGINT -> precision == 19 && scale == 0;
            case DECIMAL ->
                precision == LogicalTypes.decimalPrecision(column.dataType())
                        && scale == LogicalTypes.decimalScale(column.dataType());
            default -> false;
        };
    }

    private static long count(Connection connection, String sql, String owner, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, owner);
            query.setString(2, table);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Oracle metadata query returned no result");
                return rows.getLong(1);
            }
        }
    }
}
