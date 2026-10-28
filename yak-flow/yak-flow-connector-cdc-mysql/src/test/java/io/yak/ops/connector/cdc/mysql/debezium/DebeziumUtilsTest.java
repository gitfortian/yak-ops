package io.yak.ops.connector.cdc.mysql.debezium;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies upstream offset discovery behavior using YakFlow's JDBC/DBZ 2.7 connection boundary. */
class DebeziumUtilsTest {

    @Test
    void capturesFilePositionAndGtidFromMySqlStatus() throws SQLException {
        AtomicInteger statements = new AtomicInteger();
        BinlogOffset found = DebeziumUtils.currentBinlogOffset(fakeConnection(false, 5, statements));
        assertThat(found.getFilename()).isEqualTo("mysql-bin.000008");
        assertThat(found.getPosition()).isEqualTo(123L);
        assertThat(found.getGtidSet()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-2");
        assertThat(statements.get()).isEqualTo(1);
    }

    @Test
    void fallsBackToLegacyMasterStatusWithoutGtidColumn() throws SQLException {
        AtomicInteger statements = new AtomicInteger();
        BinlogOffset found = DebeziumUtils.currentBinlogOffset(fakeConnection(true, 4, statements));
        assertThat(found.getFilename()).isEqualTo("mysql-bin.000008");
        assertThat(found.getPosition()).isEqualTo(123L);
        assertThat(found.getGtidSet()).isNull();
        assertThat(statements.get()).isEqualTo(2);
    }

    @Test
    void snapshotWatermarksUseSameStatusParserAndPreserveGtid() throws SQLException {
        AtomicInteger queries = new AtomicInteger();
        Connection connection = fakeConnection(false, 5, queries);
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(60);
        BinlogOffset watermark = DebeziumUtils.currentBinlogOffset(statement);
        assertThat(watermark.getFilename()).isEqualTo("mysql-bin.000008");
        assertThat(watermark.getPosition()).isEqualTo(123L);
        assertThat(watermark.getGtidSet()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-2");
        assertThat(queries.get()).isEqualTo(1);
    }

    @Test
    void refusesBinlogStatusWithoutValidFileOrPosition() {
        ResultSet rows = proxy(ResultSet.class, (self, method, args) -> switch (method.getName()) {
            case "next" -> true;
            case "getString" -> "";
            case "getLong" -> 0L;
            default -> null;
        });
        Statement statement = proxy(Statement.class, (self, method, args) ->
                method.getName().equals("executeQuery") ? rows : null);
        assertThatThrownBy(() -> DebeziumUtils.currentBinlogOffset(statement))
                .isInstanceOf(SQLException.class);
    }

    private static Connection fakeConnection(boolean fallback, int columns, AtomicInteger statements) {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (self, method, args) -> {
            if (method.getName().equals("getColumnCount")) return columns;
            return null;
        });
        ResultSet rows = proxy(ResultSet.class, (self, method, args) -> {
            return switch (method.getName()) {
                case "next" -> true;
                case "getString" -> ((int) args[0]) == 1
                        ? "mysql-bin.000008"
                        : "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-2";
                case "getLong" -> 123L;
                case "getMetaData" -> metadata;
                default -> null;
            };
        });
        Statement statement = proxy(Statement.class, (self, method, args) -> {
            if (method.getName().equals("executeQuery")) {
                statements.incrementAndGet();
                if (fallback && args[0].equals("SHOW BINARY LOG STATUS")) {
                    throw new SQLException("MySQL version does not support BINARY LOG STATUS");
                }
                return rows;
            }
            return null;
        });
        return proxy(Connection.class, (self, method, args) ->
                method.getName().equals("createStatement") ? statement : null);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
