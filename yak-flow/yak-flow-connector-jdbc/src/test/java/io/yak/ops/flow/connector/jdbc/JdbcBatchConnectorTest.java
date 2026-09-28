package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.flow.connector.jdbc.source.JdbcSourceSplit;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalRuntime;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class JdbcBatchConnectorTest {

    private static final String DRIVER = "org.h2.Driver";
    private static final JdbcConnectionProvider DIRECT_CONNECTION = (connection, timeoutSeconds) -> {
        Class.forName(connection.driverClassName());
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        properties.setProperty("user", connection.username());
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return DriverManager.getConnection(connection.jdbcUrl(), properties);
    };

    @Test
    void shouldEnumerateExplicitNumericRangeSplits() throws Exception {
        YakTableSchema schema = sourceSchema();
        DataSourceTablePath table = new DataSourceTablePath(null, null, "source_table");
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        connection("MYSQL", "jdbc:h2:mem:split_enumerator;MODE=MySQL;DB_CLOSE_DELAY=-1"),
                        table,
                        schema,
                        2,
                        2,
                        5,
                        new JdbcNumericSplitConfig("id", 1, 10, 3)),
                DIRECT_CONNECTION);

        try (var enumerator = source.createEnumerator()) {
            JdbcSourceSplit first = enumerator.nextSplit().orElseThrow();
            JdbcSourceSplit second = enumerator.nextSplit().orElseThrow();
            JdbcSourceSplit third = enumerator.nextSplit().orElseThrow();

            assertRange(first, 1, 4);
            assertRange(second, 5, 8);
            assertRange(third, 9, 10);
            assertFalse(enumerator.nextSplit().isPresent());
            assertEquals(true, enumerator.isFinished());
        }
    }

    @Test
    void shouldSyncAllRowsAcrossNumericRangeSplits() throws Exception {
        TestDataSourceConnection sourceConnection =
                connection("MYSQL", "jdbc:h2:mem:range_source;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection =
                connection("MYSQL", "jdbc:h2:mem:range_target;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        createRangeSource(sourceConnection);
        createTarget(targetConnection);

        YakTableSchema schema = sourceSchema();
        DataSourceTablePath sourceTable = new DataSourceTablePath(null, null, "source_table");
        DataSourceTablePath targetTable = new DataSourceTablePath(null, null, "target_table");
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        sourceTable,
                        schema,
                        2,
                        2,
                        5,
                        new JdbcNumericSplitConfig("id", 1, 10, 3)),
                DIRECT_CONNECTION);
        JdbcSink sink =
                new JdbcSink(new JdbcSinkConfig(targetConnection, targetTable, 2, 5), DIRECT_CONNECTION);

        assertEquals(
                ExecutionStatus.SUCCEEDED,
                new LocalRuntime().start(source, sink, schema).await(Duration.ofSeconds(5)));

        try (var connection = DIRECT_CONNECTION.open(targetConnection, 5);
                var statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*), MIN(id), MAX(id) FROM target_table")) {
            assertEquals(true, resultSet.next());
            assertEquals(10L, resultSet.getLong(1));
            assertEquals(1L, resultSet.getLong(2));
            assertEquals(10L, resultSet.getLong(3));
        }
    }

    @Test
    void shouldRejectNumericSplitOnNonPrimaryKeyColumn() {
        YakTableSchema schema = sourceSchema();
        DataSourceTablePath table = new DataSourceTablePath(null, null, "source_table");

        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSourceConfig(
                        connection("MYSQL", "jdbc:h2:mem:invalid_split;MODE=MySQL;DB_CLOSE_DELAY=-1"),
                        table,
                        schema,
                        2,
                        2,
                        5,
                        new JdbcNumericSplitConfig("name", 1, 10, 3)));
    }

    @Test
    void shouldSyncMysqlSourceToMysqlSink() throws Exception {
        runSync("MYSQL", "MySQL");
    }

    @Test
    void shouldSyncMysqlSourceToPostgresqlSink() throws Exception {
        runSync("POSTGRE_SQL", "PostgreSQL");
    }

    @Test
    void shouldSyncMysqlSourceToOracleSink() throws Exception {
        runSync("ORACLE", "Oracle");
    }

    private void runSync(String targetType, String h2Mode) throws Exception {
        String suffix = targetType.toLowerCase();
        TestDataSourceConnection sourceConnection = connection(
                "MYSQL", "jdbc:h2:mem:source_" + suffix + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection = connection(
                targetType,
                "jdbc:h2:mem:target_" + suffix + ";MODE=" + h2Mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");

        createSource(sourceConnection);
        createTarget(targetConnection);

        YakTableSchema schema = sourceSchema();
        DataSourceTablePath sourceTable = new DataSourceTablePath(null, null, "source_table");
        DataSourceTablePath targetTable = new DataSourceTablePath(null, null, "target_table");

        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(sourceConnection, sourceTable, schema, 2, 2, 5), DIRECT_CONNECTION);
        JdbcSink sink =
                new JdbcSink(new JdbcSinkConfig(targetConnection, targetTable, 2, 5), DIRECT_CONNECTION);

        assertEquals(
                ExecutionStatus.SUCCEEDED,
                new LocalRuntime().start(source, sink, schema).await(Duration.ofSeconds(5)));

        try (var connection = DIRECT_CONNECTION.open(targetConnection, 5);
                var statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT id, name, amount FROM target_table ORDER BY id")) {
            assertRow(resultSet, 1L, "yak", new BigDecimal("10.25"));
            assertRow(resultSet, 2L, "flow", new BigDecimal("20.50"));
            assertRow(resultSet, 3L, "batch", new BigDecimal("30.75"));
            assertEquals(false, resultSet.next());
        }
    }

    private YakTableSchema sourceSchema() {
        return JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceColumn("name", "VARCHAR", Types.VARCHAR, 100, null, false, 2, false, null),
                new DataSourceColumn("amount", "DECIMAL", Types.DECIMAL, 10, 2, true, 3, false, null)));
    }

    private void assertRange(JdbcSourceSplit split, long lowerBound, long upperBound) {
        assertEquals("id", split.splitColumn());
        assertEquals(lowerBound, split.lowerBoundInclusive());
        assertEquals(upperBound, split.upperBoundInclusive());
    }

    private TestDataSourceConnection connection(String type, String jdbcUrl) {
        return new TestDataSourceConnection(type, jdbcUrl, DRIVER, "sa", "");
    }

    private void createSource(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE source_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
            statement.execute("INSERT INTO source_table VALUES (1, 'yak', 10.25)");
            statement.execute("INSERT INTO source_table VALUES (2, 'flow', 20.50)");
            statement.execute("INSERT INTO source_table VALUES (3, 'batch', 30.75)");
        }
    }


    private void createRangeSource(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE source_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
            for (int id = 1; id <= 10; id++) {
                statement.execute("INSERT INTO source_table VALUES (" + id + ", 'row-" + id + "', " + id + ".00)");
            }
        }
    }

    private void createTarget(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE target_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
        }
    }

    private void assertRow(ResultSet resultSet, long id, String name, BigDecimal amount) throws Exception {
        assertEquals(true, resultSet.next());
        assertEquals(id, resultSet.getLong(1));
        assertEquals(name, resultSet.getString(2));
        assertEquals(0, amount.compareTo(resultSet.getBigDecimal(3)));
    }
}
