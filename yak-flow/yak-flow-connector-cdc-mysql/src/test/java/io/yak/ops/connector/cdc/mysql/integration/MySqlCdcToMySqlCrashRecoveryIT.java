package io.yak.ops.connector.cdc.mysql.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.StartupMode;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Process-level acceptance for the real MySQL CDC pipeline and durable checkpoint boundary.
 *
 * <p>The child JVM is forcibly terminated after a committed target update, once before a
 * checkpoint and once after a checkpoint. An UPDATE trigger records externally visible sink
 * effects so replay is observed even when the final UPSERT row value is unchanged.
 */
@Tag("cdc-recovery")
@Tag("mysql")
@Timeout(360)
class MySqlCdcToMySqlCrashRecoveryIT {

    @RegisterExtension
    static final MySqlDatabase SOURCE = MySqlDatabase.cdc();

    @RegisterExtension
    static final MySqlDatabase TARGET = new MySqlDatabase("mysql-target-crash-recovery", false, true);

    @Test
    void replaysCommittedChangeAfterProcessCrashBeforeCheckpointAndDoesNotReplayAfterCheckpoint()
            throws Exception {
        verifyCrashRecovery(StartupMode.INITIAL);
    }

    @Test
    void latestOffsetReplaysOnlyBeyondItsDurableCheckpointAfterProcessCrash() throws Exception {
        verifyCrashRecovery(StartupMode.LATEST_OFFSET);
    }

    private void verifyCrashRecovery(StartupMode startupMode) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceTable = "cdc_crash_src_" + suffix;
        String targetTable = "cdc_crash_dst_" + suffix;
        String auditTable = "cdc_crash_audit_" + suffix;
        String trigger = "trg_cdc_crash_" + suffix;
        String topicPrefix = "yakflow_crash_" + suffix;
        int serverId = 59317;

        Path artifacts = Path.of("target", "test-artifacts", "cdc-process-crash", suffix).toAbsolutePath();
        Path stateDirectory = artifacts.resolve("state");
        Path controlDirectory = artifacts.resolve("control");
        Files.createDirectories(stateDirectory);
        Files.createDirectories(controlDirectory);

        Process first = null;
        Process second = null;
        Process third = null;
        try (Connection source = SOURCE.metadata().connect();
                Connection target = TARGET.metadata().connect()) {
            createSourceTable(source, sourceTable);
            createTargetTables(target, targetTable, auditTable, trigger);
            insertInitialRow(source, sourceTable);

            try {
                first = startProcess(
                        SOURCE.metadata(),
                        TARGET.metadata(),
                        sourceTable,
                        targetTable,
                        stateDirectory,
                        controlDirectory,
                        artifacts.resolve("process-1.log"),
                        1,
                        false,
                        serverId,
                        topicPrefix,
                        startupMode);
                awaitReady(first, controlDirectory.resolve("ready-1"), artifacts.resolve("process-1.log"));
                if (startupMode == StartupMode.LATEST_OFFSET) {
                    awaitLatestBootstrap(source, target, sourceTable, targetTable, first, artifacts.resolve("process-1.log"));
                }
                awaitTargetValue(target, targetTable, "initial", first, artifacts.resolve("process-1.log"));

                requestCheckpoint(first, controlDirectory, "g1-initial", artifacts.resolve("process-1.log"));
                Path checkpointFile = stateDirectory.resolve("checkpoint.bin");
                assertTrue(Files.isRegularFile(checkpointFile), "The initial Checkpoint must be durable");
                byte[] initialCheckpoint = Files.readAllBytes(checkpointFile);

                updateSource(source, sourceTable, "committed-before-checkpoint");
                awaitTargetValue(
                        target, targetTable, "committed-before-checkpoint", first, artifacts.resolve("process-1.log"));
                awaitAuditCount(
                        target,
                        auditTable,
                        "committed-before-checkpoint",
                        1,
                        first,
                        artifacts.resolve("process-1.log"));
                assertEquals(
                        1,
                        auditCount(target, auditTable, "committed-before-checkpoint"),
                        "The first delivery should create one external update effect");
                assertArrayEquals(
                        initialCheckpoint,
                        Files.readAllBytes(checkpointFile),
                        "No later Checkpoint may advance the source offset before the forced crash");

                kill(first);
                first = null;

                second = startProcess(
                        SOURCE.metadata(),
                        TARGET.metadata(),
                        sourceTable,
                        targetTable,
                        stateDirectory,
                        controlDirectory,
                        artifacts.resolve("process-2.log"),
                        2,
                        true,
                        serverId,
                        topicPrefix,
                        startupMode);
                awaitReady(second, controlDirectory.resolve("ready-2"), artifacts.resolve("process-2.log"));
                awaitAuditCount(
                        target,
                        auditTable,
                        "committed-before-checkpoint",
                        2,
                        second,
                        artifacts.resolve("process-2.log"));
                assertEquals(
                        2,
                        auditCount(target, auditTable, "committed-before-checkpoint"),
                        "A committed UPSERT before the durable Checkpoint must be replayed after process death");
                assertEquals(
                        "committed-before-checkpoint",
                        targetValue(target, targetTable),
                        "Replay must converge the target row to the source value");

                requestCheckpoint(second, controlDirectory, "g2-after-replay", artifacts.resolve("process-2.log"));
                byte[] checkpointAfterReplay = Files.readAllBytes(checkpointFile);

                updateSource(source, sourceTable, "committed-before-next-checkpoint");
                awaitTargetValue(
                        target,
                        targetTable,
                        "committed-before-next-checkpoint",
                        second,
                        artifacts.resolve("process-2.log"));
                awaitAuditCount(
                        target,
                        auditTable,
                        "committed-before-next-checkpoint",
                        1,
                        second,
                        artifacts.resolve("process-2.log"));
                assertArrayEquals(
                        checkpointAfterReplay,
                        Files.readAllBytes(checkpointFile),
                        "The second change must remain beyond the durable offset until explicitly checkpointed");

                requestCheckpoint(second, controlDirectory, "g2-after-second-change", artifacts.resolve("process-2.log"));
                assertFalse(
                        Arrays.equals(checkpointAfterReplay, Files.readAllBytes(checkpointFile)),
                        "The successful Checkpoint must publish the new source offset");
                kill(second);
                second = null;

                third = startProcess(
                        SOURCE.metadata(),
                        TARGET.metadata(),
                        sourceTable,
                        targetTable,
                        stateDirectory,
                        controlDirectory,
                        artifacts.resolve("process-3.log"),
                        3,
                        true,
                        serverId,
                        topicPrefix,
                        startupMode);
                awaitReady(third, controlDirectory.resolve("ready-3"), artifacts.resolve("process-3.log"));
                awaitStableAuditCount(
                        target,
                        auditTable,
                        "committed-before-next-checkpoint",
                        1,
                        third,
                        artifacts.resolve("process-3.log"),
                        Duration.ofSeconds(4));
                assertEquals(
                        2,
                        auditCount(target, auditTable, "committed-before-checkpoint"),
                        "A previously replayed change must not replay again after its Checkpoint");
                assertEquals(
                        "committed-before-next-checkpoint",
                        targetValue(target, targetTable),
                        "Recovery from the newer Checkpoint must preserve the latest row");
            } finally {
                killIfAlive(third);
                killIfAlive(second);
                killIfAlive(first);
                dropTargetTables(target, targetTable, auditTable, trigger);
                dropSourceTable(source, sourceTable);
            }
        }
    }

    private static Process startProcess(
            DatabaseMetadata source,
            DatabaseMetadata target,
            String sourceTable,
            String targetTable,
            Path stateDirectory,
            Path controlDirectory,
            Path log,
            int generation,
            boolean restore,
            int serverId,
            String topicPrefix,
            StartupMode startupMode)
            throws IOException {
        Files.createDirectories(log.getParent());
        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null || classPath.isBlank()) classPath = System.getProperty("java.class.path");
        String executable = Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                                ? "java.exe"
                                : "java")
                .toString();
        ProcessBuilder builder = new ProcessBuilder(
                        executable,
                        "-cp",
                        classPath,
                        MySqlCdcCrashRecoveryProcess.class.getName(),
                        source.host(),
                        Integer.toString(source.port()),
                        sourceTable,
                        target.url(),
                        targetTable,
                        stateDirectory.toString(),
                        controlDirectory.toString(),
                        Integer.toString(generation),
                        Boolean.toString(restore),
                        Integer.toString(serverId),
                        topicPrefix,
                        startupMode.value())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().put("YAK_CDC_SOURCE_USERNAME", source.username());
        builder.environment().put("YAK_CDC_SOURCE_PASSWORD", source.password());
        builder.environment().put("YAK_CDC_TARGET_USERNAME", target.username());
        builder.environment().put("YAK_CDC_TARGET_PASSWORD", target.password());
        return builder.start();
    }

    private static void requestCheckpoint(Process process, Path controlDirectory, String token, Path log)
            throws Exception {
        Path request = controlDirectory.resolve("checkpoint-" + token + ".request");
        Path temporary = controlDirectory.resolve("checkpoint-" + token + ".tmp");
        Path acknowledgement = controlDirectory.resolve("checkpoint-" + token + ".ack");
        Path failure = controlDirectory.resolve("checkpoint-" + token + ".failed");
        Files.writeString(temporary, token);
        Files.move(temporary, request, StandardCopyOption.ATOMIC_MOVE);
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            if (Files.exists(failure)) {
                throw new AssertionError("Child Checkpoint failed: " + Files.readString(failure) + logContents(log));
            }
            if (Files.exists(acknowledgement)) {
                assertTrue(Long.parseLong(Files.readString(acknowledgement).trim()) > 0);
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for Checkpoint acknowledgement" + logContents(log));
    }

    private static void awaitReady(Process process, Path ready, Path log) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            if (Files.exists(ready)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Child CDC process did not reach STREAMING" + logContents(log));
    }

    /**
     * Latest-offset does not copy the row inserted before its subscription.
     * Generate distinct warmup updates until the real Debezium subscription is active,
     * then establish the same initial Sink value used by the durable crash assertions.
     */
    private static void awaitLatestBootstrap(
            Connection source, Connection target, String sourceTable, String targetTable, Process process, Path log)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        int attempt = 0;
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            updateSource(source, sourceTable, "bootstrap-" + attempt++);
            if (targetValue(target, targetTable) != null) {
                updateSource(source, sourceTable, "initial");
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Latest-offset Binlog subscription did not become ready" + logContents(log));
    }

    private static void awaitTargetValue(
            Connection target, String table, String expected, Process process, Path log) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            if (expected.equals(targetValue(target, table))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Target did not converge to " + expected + logContents(log));
    }

    private static void awaitAuditCount(
            Connection target,
            String auditTable,
            String name,
            int minimum,
            Process process,
            Path log)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            if (auditCount(target, auditTable, name) >= minimum) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Expected at least " + minimum + " audit effects for " + name + logContents(log));
    }

    private static void awaitStableAuditCount(
            Connection target,
            String auditTable,
            String name,
            int expected,
            Process process,
            Path log,
            Duration stableFor)
            throws Exception {
        long deadline = System.nanoTime() + stableFor.toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessAlive(process, log);
            assertEquals(expected, auditCount(target, auditTable, name), "Unexpected replay after durable Checkpoint");
            Thread.sleep(100);
        }
    }

    private static void assertProcessAlive(Process process, Path log) throws IOException {
        assertTrue(process.isAlive(), "CDC child process exited unexpectedly" + logContents(log));
    }

    private static void kill(Process process) throws Exception {
        if (process == null) return;
        process.destroyForcibly();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "The CDC child process could not be killed");
        Thread.sleep(500);
    }

    private static void killIfAlive(Process process) throws Exception {
        if (process != null && process.isAlive()) kill(process);
    }

    private static String logContents(Path log) {
        try {
            String value = Files.exists(log) ? Files.readString(log) : "";
            return value.replace(SOURCE.metadata().password(), "[REDACTED]")
                    .replace(TARGET.metadata().password(), "[REDACTED]");
        } catch (Exception unavailable) {
            return " (child log unavailable: " + unavailable.getClass().getSimpleName() + ")";
        }
    }

    private static void createSourceTable(Connection connection, String table) throws Exception {
        execute(connection, "DROP TABLE IF EXISTS " + quote(table));
        execute(connection, "CREATE TABLE " + quote(table)
                + " (ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(64) NOT NULL) ENGINE=InnoDB");
    }

    private static void createTargetTables(Connection connection, String table, String audit, String trigger)
            throws Exception {
        execute(connection, "DROP TRIGGER IF EXISTS " + quote(trigger));
        execute(connection, "DROP TABLE IF EXISTS " + quote(audit));
        execute(connection, "DROP TABLE IF EXISTS " + quote(table));
        execute(connection, "CREATE TABLE " + quote(table)
                + " (ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(64) NOT NULL) ENGINE=InnoDB");
        execute(connection, "CREATE TABLE " + quote(audit)
                + " (ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, NAME VARCHAR(64) NOT NULL) ENGINE=InnoDB");
        execute(connection, "CREATE TRIGGER " + quote(trigger) + " AFTER UPDATE ON " + quote(table)
                + " FOR EACH ROW INSERT INTO " + quote(audit) + " (NAME) VALUES (NEW.NAME)");
    }

    private static void insertInitialRow(Connection connection, String table) throws Exception {
        execute(connection, "INSERT INTO " + quote(table) + " (ID, NAME) VALUES (1, 'initial')");
    }

    private static void updateSource(Connection connection, String table, String name) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement("UPDATE " + quote(table) + " SET NAME=? WHERE ID=1")) {
            statement.setString(1, name);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String targetValue(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT NAME FROM " + quote(table) + " WHERE ID=1")) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static int auditCount(Connection connection, String table, String name) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT COUNT(*) FROM " + quote(table) + " WHERE NAME=?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void dropTargetTables(Connection connection, String table, String audit, String trigger)
            throws Exception {
        execute(connection, "DROP TRIGGER IF EXISTS " + quote(trigger));
        execute(connection, "DROP TABLE IF EXISTS " + quote(audit));
        execute(connection, "DROP TABLE IF EXISTS " + quote(table));
    }

    private static void dropSourceTable(Connection connection, String table) throws Exception {
        execute(connection, "DROP TABLE IF EXISTS " + quote(table));
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
