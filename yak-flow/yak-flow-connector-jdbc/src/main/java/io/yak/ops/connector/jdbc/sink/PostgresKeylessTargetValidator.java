package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Verifies physical PostgreSQL full-row retraction safety. */
public final class PostgresKeylessTargetValidator {

    private PostgresKeylessTargetValidator() {}

    public static void validate(Connection connection, JdbcTableWritePlan plan) throws SQLException {
        JdbcKeylessRetractionContract.validate(plan.sourceSchema(), plan.schema());
        String owner = plan.targetTable().schema() == null
                ? connection.getSchema()
                : plan.targetTable().schema();
        if (owner == null || owner.isBlank()) {
            throw new SQLException("PostgreSQL RETRACT requires a target schema");
        }
        long target = heap(connection, owner, plan.targetTable().table());
        if (count(connection, "SELECT COUNT(*) FROM pg_catalog.pg_index WHERE indrelid = ? AND indisunique", target)
                != 0) {
            throw new SQLException("PostgreSQL RETRACT target must have no unique index");
        }
        requireNoSideEffects(connection, target);
        validateColumns(connection, target, plan.schema());
    }

    private static long heap(Connection connection, String owner, String name) throws SQLException {
        String tableSql = "SELECT c.oid, c.relkind, c.relpersistence, c.relrowsecurity "
                + "FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement query = connection.prepareStatement(tableSql)) {
            query.setString(1, owner);
            query.setString(2, name);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()
                        || !"r".equals(rows.getString(2))
                        || !"p".equals(rows.getString(3))
                        || rows.getBoolean(4)) {
                    throw new SQLException("PostgreSQL RETRACT requires a permanent heap table without RLS: " + name);
                }
                long oid = rows.getLong(1);
                if (rows.next()) throw new SQLException("Ambiguous PostgreSQL target table");
                return oid;
            }
        }
    }

    private static void requireNoSideEffects(Connection connection, long oid) throws SQLException {
        String triggerSql = "SELECT COUNT(*) FROM pg_catalog.pg_trigger WHERE tgrelid = ? AND NOT tgisinternal";
        String foreignKeySql =
                "SELECT COUNT(*) FROM pg_catalog.pg_constraint WHERE contype = 'f' AND (conrelid = ? OR confrelid = ?)";
        String ruleSql = "SELECT COUNT(*) FROM pg_catalog.pg_rewrite WHERE ev_class = ? AND rulename <> '_RETURN'";
        boolean triggers = count(connection, triggerSql, oid) != 0;
        boolean foreignKeys = count(connection, foreignKeySql, oid, oid) != 0;
        boolean rules = count(connection, ruleSql, oid) != 0;
        if (triggers || foreignKeys || rules) {
            throw new SQLException("PostgreSQL RETRACT table has triggers, rules or foreign-key side effects");
        }
    }

    private static void validateColumns(Connection connection, long oid, TableSchema schema) throws SQLException {
        String columnSql = "SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod), "
                + "a.attnotnull, a.attgenerated, a.attidentity FROM pg_catalog.pg_attribute a "
                + "WHERE a.attrelid = ? AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum";
        try (PreparedStatement query = connection.prepareStatement(columnSql)) {
            query.setLong(1, oid);
            try (ResultSet rows = query.executeQuery()) {
                int count = schema.columnCount();
                for (int index = 0; index < count; index++) {
                    if (!rows.next()) {
                        throw new SQLException("PostgreSQL RETRACT table does not contain every expected column");
                    }
                    String name = rows.getString(1);
                    String nativeType = rows.getString(2).toLowerCase(Locale.ROOT);
                    boolean notNull = rows.getBoolean(3);
                    if (!rows.getString(4).isEmpty() || !rows.getString(5).isEmpty()) {
                        throw new SQLException("PostgreSQL RETRACT cannot use generated or identity columns");
                    }
                    Column column = schema.column(index);
                    if (!column.name().equals(name) || column.nullable() == notNull || !matches(column, nativeType)) {
                        throw new SQLException("Unsupported PostgreSQL RETRACT column metadata: " + name);
                    }
                }
                if (rows.next()) {
                    throw new SQLException("PostgreSQL RETRACT requires complete physical column coverage");
                }
            }
        }
    }

    private static boolean matches(Column column, String nativeType) {
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> nativeType.equals("boolean");
            case SMALLINT -> nativeType.equals("smallint");
            case INTEGER -> nativeType.equals("integer");
            case BIGINT -> nativeType.equals("bigint");
            case DECIMAL -> nativeType.equals(decimalType(column));
            case BINARY, VARBINARY -> nativeType.equals("bytea");
            default -> false;
        };
    }

    private static String decimalType(Column column) {
        return "numeric(" + LogicalTypes.decimalPrecision(column.dataType()) + ","
                + LogicalTypes.decimalScale(column.dataType()) + ")";
    }

    private static long count(Connection connection, String sql, long... ids) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) query.setLong(i + 1, ids[i]);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("PostgreSQL metadata query returned no rows");
                return rows.getLong(1);
            }
        }
    }
}
