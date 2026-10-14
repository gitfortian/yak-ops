package io.yak.ops.connector.jdbc.source;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

/** Acquires a typed source-side upper bound before any target mutation, never embedding user SQL. */
public final class JdbcIncrementalUpperBound {
    private JdbcIncrementalUpperBound() {}

    public static String read(
            JdbcConnectionOptions settings,
            TableId table,
            JdbcIncrementalRange.Mode mode,
            String column,
            long delaySeconds)
            throws SQLException {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(mode, "mode");
        var dialect = JdbcDialects.forUrl(settings.url());
        String url = settings.url();
        String sql =
                switch (mode) {
                    case TIMESTAMP ->
                        url.startsWith("jdbc:mysql:")
                                ? "SELECT UTC_TIMESTAMP(6)"
                                : url.startsWith("jdbc:postgresql:")
                                        ? "SELECT CURRENT_TIMESTAMP AT TIME ZONE 'UTC'"
                                        : "SELECT CURRENT_TIMESTAMP";
                    case INCREASING ->
                        "SELECT MAX(" + dialect.quoteIdentifier(column) + ") FROM " + dialect.qualifiedTable(table);
                };
        try (var connection = new DefaultJdbcConnectionProvider(settings).getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Cannot determine JDBC incremental upper bound");
                if (mode == JdbcIncrementalRange.Mode.TIMESTAMP) {
                    var value = result.getTimestamp(1);
                    if (value == null) throw new SQLException("Source clock returned no timestamp");
                    LocalDateTime upper = value.toLocalDateTime().minusSeconds(delaySeconds);
                    return upper.toString();
                }
                var value = result.getBigDecimal(1);
                if (value == null) return "0";
                BigInteger upper = value.toBigIntegerExact();
                if (upper.signum() < 0) throw new SQLException("Increasing source requires nonnegative keys");
                return upper.toString();
            } catch (ArithmeticException invalid) {
                throw new SQLException("Increasing source key exceeds supported integer representation", invalid);
            }
        }
    }
}
