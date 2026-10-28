package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;

/** JDBC keyset boundaries whose ordering is determined by the source database. */
public final class StatementUtils {
    private StatementUtils() {}

    /**
     * Returns the greatest key among the next requested rows. This can equal the lower
     * boundary when multiple physical rows share one chunk-column value.
     */
    public static Object queryNextChunkMax(
            Connection connection,
            JdbcDialect dialect,
            TableId table,
            String column,
            LogicalTypeRoot type,
            int chunkSize,
            Object lower)
            throws SQLException {
        String key = dialect.quoteIdentifier(column);
        String sql = "SELECT MAX(" + key + ") FROM (SELECT " + key + " FROM " + dialect.qualifiedTable(table)
                + (lower == null ? "" : " WHERE " + key + " >= ?")
                + " ORDER BY " + key + " LIMIT ?) chunk_values";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (lower != null) setSafeObject(statement, index++, lower);
            statement.setInt(index, chunkSize);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Snapshot chunk maximum query returned no result");
                return read(result, type);
            }
        }
    }

    /**
     * Finds the first strictly greater key. This prevents splitting identical values
     * between chunks, including values equivalent under a case-insensitive MySQL collation.
     */
    public static Object queryMin(
            Connection connection,
            JdbcDialect dialect,
            TableId table,
            String column,
            LogicalTypeRoot type,
            Object after)
            throws SQLException {
        String key = dialect.quoteIdentifier(column);
        String sql = "SELECT MIN(" + key + ") FROM " + dialect.qualifiedTable(table) + " WHERE " + key + " > ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setSafeObject(statement, 1, after);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Snapshot next chunk query returned no result");
                return read(result, type);
            }
        }
    }

    /**
     * PreparedStatement#setObject may narrow a MySQL BIGINT UNSIGNED parameter to a signed long.
     * Ported from Flink CDC StatementUtils#setSafeObject.
     */
    public static void setSafeObject(PreparedStatement ps, int parameterIndex, Object value) throws SQLException {
        if (value instanceof BigInteger) {
            BigInteger bigIntValue = (BigInteger) value;
            if (bigIntValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    || bigIntValue.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                ps.setBigDecimal(parameterIndex, new BigDecimal(bigIntValue));
            } else {
                ps.setObject(parameterIndex, bigIntValue.longValueExact());
            }
        } else {
            ps.setObject(parameterIndex, value);
        }
    }

    /**
     * Builds the same bounded snapshot scan used by YakFlow's typed chunk reader.
     * Flink CDC's RowType-based split scan SQL is adapted to YakFlow's TableSchema and
     * half-open chunk boundaries without changing the generated SQL semantics.
     */
    public static String buildSplitScanQuery(
            JdbcDialect dialect,
            TableId table,
            TableSchema schema,
            String splitColumn,
            boolean isFirstSplit,
            boolean isLastSplit) {
        String quotedColumn = dialect.quoteIdentifier(splitColumn);
        String projection = schema.columns().stream()
                .map(column -> dialect.quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        StringBuilder sql =
                new StringBuilder("SELECT ").append(projection).append(" FROM ").append(dialect.qualifiedTable(table));
        if (!isFirstSplit || !isLastSplit) {
            sql.append(" WHERE ");
            if (!isFirstSplit) {
                sql.append(quotedColumn).append(" >= ?");
            }
            if (!isFirstSplit && !isLastSplit) {
                sql.append(" AND ");
            }
            if (!isLastSplit) {
                sql.append(quotedColumn).append(" < ?");
            }
        }
        return sql.append(" ORDER BY ").append(quotedColumn).toString();
    }

    private static Object read(ResultSet result, LogicalTypeRoot type) throws SQLException {
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> {
                BigDecimal number = result.getBigDecimal(1);
                if (number == null) yield null;
                try {
                    yield number.longValueExact();
                } catch (ArithmeticException exception) {
                    throw new SQLException("MySQL snapshot chunk key is outside signed BIGINT range", exception);
                }
            }
            case DECIMAL -> result.getBigDecimal(1);
            case CHAR, VARCHAR -> result.getString(1);
            default -> throw new SQLException("Unsupported MySQL snapshot chunk type: " + type);
        };
    }
}
