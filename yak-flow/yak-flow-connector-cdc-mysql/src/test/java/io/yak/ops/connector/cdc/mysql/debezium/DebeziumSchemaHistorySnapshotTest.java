package io.yak.ops.connector.cdc.mysql.debezium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebeziumSchemaHistorySnapshotTest {

    @TempDir
    Path directory;

    @Test
    void snapshotKeepsCompleteHistoryRecordsAndDropsAnInProgressTrailingRecord() throws IOException {
        Path history = directory.resolve("schema-history.dat");
        byte[] complete = "{\"ddl\":\"CREATE TABLE orders (id INT)\"}\n".getBytes(StandardCharsets.UTF_8);
        byte[] partial = "{\"ddl\":\"ALTER TABLE orders ADD COLUMN note VARCHAR(32)\""
                .getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[complete.length + partial.length];
        System.arraycopy(complete, 0, file, 0, complete.length);
        System.arraycopy(partial, 0, file, complete.length, partial.length);
        Files.write(history, file);

        assertThat(DebeziumSchemaHistorySnapshot.readComplete(history, 1024)).isEqualTo(complete);
    }

    @Test
    void snapshotReturnsEmptyWhenNoCompleteHistoryRecordExistsYet() throws IOException {
        Path history = directory.resolve("schema-history.dat");
        Files.writeString(history, "{\"ddl\":\"CREATE TABLE orders");
        assertThat(DebeziumSchemaHistorySnapshot.readComplete(history, 1024)).isEmpty();
        assertThat(DebeziumSchemaHistorySnapshot.completePrefix(new byte[0])).isEmpty();
    }

    @Test
    void snapshotPreservesACompleteFileWithoutTrimmingItsBytes() throws IOException {
        Path history = directory.resolve("schema-history.dat");
        byte[] bytes = "{\"ddl\":\"CREATE TABLE orders (id INT)\"}\n"
                .concat("{\"ddl\":\"ALTER TABLE orders ADD COLUMN note VARCHAR(32)\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(history, bytes);
        assertThat(DebeziumSchemaHistorySnapshot.readComplete(history, 1024)).isEqualTo(bytes);
    }

    @Test
    void snapshotFailsClosedWhenHistoryExceedsCheckpointBudget() throws IOException {
        Path history = directory.resolve("schema-history.dat");
        Files.writeString(history, "x".repeat(33));
        assertThatThrownBy(() -> DebeziumSchemaHistorySnapshot.readComplete(history, 32))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds size limit");
    }

    @Test
    void historicalStartupAnchorsSchemaOnlyHistoryAtChosenOffset() throws Exception {
        var requested = new DebeziumOffset(
                java.util.Map.of("server", "shop"),
                java.util.Map.of("file", "mysql-bin.000001", "pos", 200L));
        byte[] original = ("{\"source\":{\"server\":\"shop\"},"
                        + "\"position\":{\"file\":\"mysql-bin.000003\",\"pos\":500},"
                        + "\"ddl\":\"CREATE TABLE shop.orders (id BIGINT)\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] rewritten = DebeziumSchemaHistorySnapshot.anchorBootstrap(original, requested);
        String result = new String(rewritten, StandardCharsets.UTF_8);
        assertThat(result).contains("\"file\":\"mysql-bin.000001\"");
        assertThat(result).contains("\"pos\":199");
        assertThat(result).contains("CREATE TABLE shop.orders");
        assertThat(result).doesNotContain("mysql-bin.000003");
    }

    @Test
    void restoreSanitizationRetainsCompletePrefixOfOlderCheckpoint() {
        byte[] history = ("{\"ddl\":\"CREATE TABLE orders (id INT)\"}\n"
                        + "{\"ddl\":\"partial")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(DebeziumSchemaHistorySnapshot.completePrefix(history))
                .isEqualTo("{\"ddl\":\"CREATE TABLE orders (id INT)\"}\n".getBytes(StandardCharsets.UTF_8));
    }
}
