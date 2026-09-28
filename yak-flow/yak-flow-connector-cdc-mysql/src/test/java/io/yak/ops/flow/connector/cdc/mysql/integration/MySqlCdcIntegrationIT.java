package io.yak.ops.flow.connector.cdc.mysql.integration;

import static org.junit.jupiter.api.Assertions.fail;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;

/**
 * 真实 MySQL + Binlog 的 CDC 集成测试，覆盖 initial snapshot、增删改、checkpoint 和状态目录复用重启。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
class MySqlCdcIntegrationIT {

    private static final String DATABASE = "yakflow";
    private static final String ROOT_PASSWORD = "yak-root";
    private static final long SERVER_ID = 54021L;
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("root")
            .withPassword(ROOT_PASSWORD)
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand(
                    "--server-id=223344",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--binlog-row-image=FULL");
    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakDataType.BIGINT, false, null, null, null),
                    new YakColumn("name", YakDataType.STRING, true, 100, null, null)),
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

    @TempDir
    Path stateDirectory;

    @BeforeAll
    static void startMySql() {
        MYSQL.start();
    }

    @AfterAll
    static void stopMySql() {
        MYSQL.stop();
    }

    @Test
    void shouldSyncSnapshotBinlogAndReusePersistedOffsetAfterRestart() throws Exception {
        resetTables();
        JdbcConnectionProperties connection = connection();
        MySqlCdcSourceConfig sourceConfig = MySqlCdcSourceConfig.defaults(
                connection,
                new DataSourceTablePath(DATABASE, null, "source_user"),
                SCHEMA,
                stateDirectory,
                "source-user-cdc",
                SERVER_ID);

        LocalExecution<?> firstExecution = startExecution(sourceConfig, connection);
        try {
            awaitTarget(Map.of(1L, "alpha", 2L, "beta"));

            execute("INSERT INTO source_user(id, name) VALUES (3, 'gamma')");
            execute("UPDATE source_user SET name = 'alpha-v2' WHERE id = 1");
            execute("DELETE FROM source_user WHERE id = 2");

            awaitTarget(Map.of(1L, "alpha-v2", 3L, "gamma"));
            firstExecution.checkpoint().get(15, TimeUnit.SECONDS);
            awaitOffsetFile();
        } finally {
            stopExecution(firstExecution);
        }

        execute("INSERT INTO source_user(id, name) VALUES (4, 'delta')");

        LocalExecution<?> secondExecution = startExecution(sourceConfig, connection);
        try {
            awaitTarget(Map.of(1L, "alpha-v2", 3L, "gamma", 4L, "delta"));
            secondExecution.checkpoint().get(15, TimeUnit.SECONDS);
        } finally {
            stopExecution(secondExecution);
        }
    }

    private LocalExecution<?> startExecution(
            MySqlCdcSourceConfig sourceConfig, JdbcConnectionProperties connection) {
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        connection,
                        new DataSourceTablePath(DATABASE, null, "target_user"),
                        100,
                        10,
                        JdbcWriteMode.CHANGELOG),
                DIRECT_CONNECTION);
        return new LocalExecutionEngine(Duration.ofMillis(250))
                .start(new MySqlCdcSource(sourceConfig), sink, SCHEMA);
    }

    private void stopExecution(LocalExecution<?> execution) throws Exception {
        if (execution.status() == ExecutionStatus.RUNNING) {
            execution.cancel();
        }
        execution.await(Duration.ofSeconds(15));
    }

    private JdbcConnectionProperties connection() {
        return new JdbcConnectionProperties(
                "MYSQL",
                MYSQL.getHost(),
                MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getJdbcUrl(),
                "com.mysql.cj.jdbc.Driver",
                "MYSQL_8",
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                DATABASE,
                null,
                Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"),
                SshTunnelConfig.disabled(),
                "{}");
    }

    private void resetTables() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS target_user");
            statement.execute("DROP TABLE IF EXISTS source_user");
            statement.execute("CREATE TABLE source_user (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            statement.execute("CREATE TABLE target_user (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            statement.execute("INSERT INTO source_user(id, name) VALUES (1, 'alpha'), (2, 'beta')");
        }
    }

    private void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void awaitTarget(Map<Long, String> expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        Map<Long, String> actual = Map.of();
        while (System.nanoTime() < deadline) {
            actual = readTarget();
            if (actual.equals(expected)) {
                return;
            }
            Thread.sleep(100);
        }
        fail("target rows did not converge, expected=" + expected + ", actual=" + actual);
    }

    private Map<Long, String> readTarget() throws Exception {
        Map<Long, String> values = new LinkedHashMap<>();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT id, name FROM target_user ORDER BY id")) {
            while (resultSet.next()) {
                values.put(resultSet.getLong(1), resultSet.getString(2));
            }
        }
        return values;
    }

    private void awaitOffsetFile() throws Exception {
        Path offsetFile = stateDirectory.resolve("offsets.dat");
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(offsetFile) && Files.size(offsetFile) > 0) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Debezium offset file was not persisted after checkpoint");
    }
}
