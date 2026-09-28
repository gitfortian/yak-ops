package io.yak.ops.flow.connector.jdbc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSaveMode;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * 第一阶段离线同步真实数据库验收：MySQL Source -> MySQL / PostgreSQL / Oracle。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
class OfflineSyncJdbcAcceptanceIT {

    private static final String MYSQL_DATABASE = "yakflow";
    private static final String ORACLE_PASSWORD = "yakoracle";

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(MYSQL_DATABASE)
            .withUsername("yak")
            .withPassword("yakpass");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yakflow")
            .withUsername("yak")
            .withPassword("yakpass");

    private static final GenericContainer<?> ORACLE = new GenericContainer<>(
                    DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
            .withEnv("ORACLE_PASSWORD", ORACLE_PASSWORD)
            .withExposedPorts(1521)
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(6));

    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, false, 100),
                    new YakColumn("amount", YakTypes.decimal(10, 2), true, null)),
            List.of("id"));

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

    @BeforeAll
    static void startDatabases() throws Exception {
        Startables.deepStart(Stream.of(MYSQL, POSTGRES, ORACLE)).join();
        createSource();
        createTargets();
    }

    @AfterAll
    static void stopDatabases() {
        ORACLE.stop();
        POSTGRES.stop();
        MYSQL.stop();
    }

    @Test
    void shouldSyncMysqlToMysql() throws Exception {
        executeTarget(
                mysqlConnection(),
                new DataSourceTablePath(MYSQL_DATABASE, null, "target_mysql"),
                "INSERT INTO target_mysql VALUES (999, 'stale', 999.99)",
                "SELECT id, name, amount FROM target_mysql ORDER BY id");
    }

    @Test
    void shouldSyncMysqlToPostgresql() throws Exception {
        executeTarget(
                postgresConnection(),
                new DataSourceTablePath("yakflow", "public", "target_pg"),
                "INSERT INTO target_pg VALUES (999, 'stale', 999.99)",
                "SELECT id, name, amount FROM target_pg ORDER BY id");
    }

    @Test
    void shouldSyncMysqlToOracle() throws Exception {
        executeTarget(
                oracleConnection(),
                new DataSourceTablePath(null, null, "target_oracle"),
                "INSERT INTO \"target_oracle\" (\"id\", \"name\", \"amount\") VALUES (999, 'stale', 999.99)",
                "SELECT \"id\", \"name\", \"amount\" FROM \"target_oracle\" ORDER BY \"id\"");
    }

    private void executeTarget(
            DataSourceConnection target, DataSourceTablePath targetTable, String staleInsertSql, String query)
            throws Exception {
        runSync(target, targetTable, JdbcSaveMode.APPEND);
        assertTargetRows(target, query);

        try (var connection = DIRECT_CONNECTION.open(target, 10);
                var statement = connection.createStatement()) {
            statement.execute(staleInsertSql);
        }

        runSync(target, targetTable, JdbcSaveMode.OVERWRITE);
        assertTargetRows(target, query);

        try (var connection = DIRECT_CONNECTION.open(target, 10);
                var statement = connection.createStatement()) {
            statement.execute(staleInsertSql);
        }
        runUpsert(target, targetTable);
        assertUpsertRows(target, query);
    }

    private void runSync(DataSourceConnection target, DataSourceTablePath targetTable, JdbcSaveMode saveMode)
            throws Exception {
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        mysqlConnection(),
                        new DataSourceTablePath(MYSQL_DATABASE, null, "source_table"),
                        SCHEMA,
                        2,
                        2,
                        10,
                        1L),
                DIRECT_CONNECTION);
        JdbcSink sink =
                new JdbcSink(new JdbcSinkConfig(target, targetTable, 2, 10, saveMode), DIRECT_CONNECTION);
        LocalExecution<?> execution = new LocalExecutionEngine().start(source, sink, SCHEMA, 3);

        assertEquals(ExecutionStatus.SUCCEEDED, execution.await(Duration.ofSeconds(30)));
        assertEquals(new ExecutionMetrics(3, 3), execution.metrics());
    }

    private void runUpsert(DataSourceConnection target, DataSourceTablePath targetTable) throws Exception {
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        target,
                        targetTable,
                        2,
                        10,
                        JdbcSaveMode.APPEND,
                        JdbcWriteMode.UPSERT),
                DIRECT_CONNECTION);
        try (var writer = sink.createWriter(SCHEMA)) {
            writer.open();
            writer.write(List.of(
                    new YakRow(RowKind.INSERT, List.of(1L, "upserted", new BigDecimal("11.11"))),
                    new YakRow(RowKind.INSERT, List.of(4L, "new-row", new BigDecimal("40.00")))));
            writer.flush();
        }
    }

    private void assertUpsertRows(DataSourceConnection target, String query) throws Exception {
        try (var connection = DIRECT_CONNECTION.open(target, 10);
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(query)) {
            assertRow(resultSet, 1L, "upserted", new BigDecimal("11.11"));
            assertRow(resultSet, 2L, "flow", new BigDecimal("20.50"));
            assertRow(resultSet, 3L, "acceptance", new BigDecimal("30.75"));
            assertRow(resultSet, 4L, "new-row", new BigDecimal("40.00"));
            assertRow(resultSet, 999L, "stale", new BigDecimal("999.99"));
            assertEquals(false, resultSet.next());
        }
    }

    private void assertTargetRows(DataSourceConnection target, String query) throws Exception {
        try (var connection = DIRECT_CONNECTION.open(target, 10);
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(query)) {
            assertRow(resultSet, 1L, "yak", new BigDecimal("10.25"));
            assertRow(resultSet, 2L, "flow", new BigDecimal("20.50"));
            assertRow(resultSet, 3L, "acceptance", new BigDecimal("30.75"));
            assertEquals(false, resultSet.next());
        }
    }

    private static void createSource() throws Exception {
        try (var connection = DIRECT_CONNECTION.open(mysqlConnection(), 10);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS source_table");
            statement.execute(
                    "CREATE TABLE source_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
            statement.execute("INSERT INTO source_table VALUES (1, 'yak', 10.25)");
            statement.execute("INSERT INTO source_table VALUES (2, 'flow', 20.50)");
            statement.execute("INSERT INTO source_table VALUES (3, 'acceptance', 30.75)");
        }
    }

    private static void createTargets() throws Exception {
        try (var connection = DIRECT_CONNECTION.open(mysqlConnection(), 10);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS target_mysql");
            statement.execute(
                    "CREATE TABLE target_mysql (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
        }
        try (var connection = DIRECT_CONNECTION.open(postgresConnection(), 10);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS target_pg");
            statement.execute(
                    "CREATE TABLE target_pg (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
        }
        try (var connection = DIRECT_CONNECTION.open(oracleConnection(), 10);
                var statement = connection.createStatement()) {
            try {
                statement.execute("DROP TABLE \"target_oracle\" PURGE");
            } catch (Exception ignored) {
                // 首次验收时目标表不存在。
            }
            statement.execute(
                    "CREATE TABLE \"target_oracle\" (\"id\" NUMBER(19) PRIMARY KEY, \"name\" VARCHAR2(100) NOT NULL, \"amount\" NUMBER(10,2))");
        }
    }

    private static DataSourceConnection mysqlConnection() {
        return new TestConnection(
                "MYSQL",
                MYSQL.getJdbcUrl(),
                "com.mysql.cj.jdbc.Driver",
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                MYSQL_DATABASE);
    }

    private static DataSourceConnection postgresConnection() {
        return new TestConnection(
                "POSTGRE_SQL",
                POSTGRES.getJdbcUrl(),
                "org.postgresql.Driver",
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                "yakflow");
    }

    private static DataSourceConnection oracleConnection() {
        String jdbcUrl = "jdbc:oracle:thin:@//" + ORACLE.getHost() + ":" + ORACLE.getMappedPort(1521) + "/FREEPDB1";
        return new TestConnection(
                "ORACLE", jdbcUrl, "oracle.jdbc.OracleDriver", "system", ORACLE_PASSWORD, null);
    }

    private static void assertRow(
            java.sql.ResultSet resultSet, long id, String name, BigDecimal amount) throws Exception {
        assertEquals(true, resultSet.next());
        assertEquals(id, resultSet.getLong(1));
        assertEquals(name, resultSet.getString(2));
        assertEquals(0, amount.compareTo(resultSet.getBigDecimal(3)));
    }

    private record TestConnection(
            String type,
            String jdbcUrl,
            String driverClassName,
            String username,
            String password,
            String database)
            implements DataSourceConnection {

        @Override
        public String schema() {
            return null;
        }

        @Override
        public Map<String, String> properties() {
            return Map.of();
        }

        @Override
        public String normalizedJson() {
            return "{}";
        }
    }
}
