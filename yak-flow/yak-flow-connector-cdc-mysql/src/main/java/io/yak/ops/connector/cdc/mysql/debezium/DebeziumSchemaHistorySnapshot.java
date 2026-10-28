package io.yak.ops.connector.cdc.mysql.debezium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Captures only complete records from Debezium 2.7's newline-delimited FileSchemaHistory.
 *
 * <p>The engine may append a later DDL while the Source mailbox checkpoints an earlier
 * delivered offset. Complete future records are retained: Debezium's SchemaHistory recovery
 * filters them against that restored offset. An incomplete trailing line is never checkpointed.
 */
public final class DebeziumSchemaHistorySnapshot {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DebeziumSchemaHistorySnapshot() {}

    /**
     * Adapt the schema-only bootstrap to an older, explicitly selected Binlog position.
     *
     * <p>Unlike a restored checkpoint, a new stream-only source has no historical
     * Schema History. Debezium's no_data bootstrap records describe the source schema
     * at initialization time, so its history positions would be filtered out by
     * AbstractSchemaHistory.recover when the requested offset is older. Anchor the
     * complete bootstrap records at the chosen start position without changing
     * row offsets, preserving the checkpoint's real mailbox progress.
     *
     * <p>This is safe only while the selected table schema is unchanged between the
     * requested point and bootstrap. If historical DDL changed a table's shape, a
     * schema-only snapshot cannot reconstruct its earlier definition; the source
     * must fail closed rather than silently claim historical schema consistency.
     */
    public static byte[] anchorBootstrap(byte[] history, DebeziumOffset selected) throws IOException {
        Objects.requireNonNull(selected, "selected");
        byte[] complete = completePrefix(history);
        if (complete.length == 0) {
            throw new IOException("Cannot anchor incomplete MySQL Schema History");
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream(complete.length);
        for (String record : new String(complete, StandardCharsets.UTF_8).split("\\n")) {
            if (record.isBlank()) continue;
            JsonNode parsed = JSON.readTree(record);
            if (!(parsed instanceof ObjectNode json) || !(json.get("position") instanceof ObjectNode position)) {
                throw new IOException("Invalid MySQL Schema History record");
            }
            position.removeAll();
            selected.position().forEach((key, value) -> position.set(key, JSON.valueToTree(value)));
            // This is the schema in force BEFORE the requested Binlog event.
            // Debezium can initialize its in-event row cursor to -1 on restore;
            // history at the same file/pos with row=0 then compares AFTER it,
            // causing recovery to skip the entire table definition.
            Object startingPosition = selected.position().get("pos");
            if (startingPosition instanceof Number number && number.longValue() > 0) {
                position.put("pos", number.longValue() - 1L);
            }
            result.write(JSON.writeValueAsBytes(json));
            result.write('\n');
        }
        return result.toByteArray();
    }

    /**
     * Reads a bounded, complete prefix of a live history file.
     *
     * <p>An empty result means the file does not yet contain a complete history record. Callers
     * which require a recoverable streaming state must reject that result.
     */
    public static byte[] readComplete(Path path, int maxBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Schema History size limit must be positive");
        }
        if (!Files.exists(path)) {
            return new byte[0];
        }
        if (Files.size(path) > maxBytes) {
            throw new IOException("MySQL CDC Schema History checkpoint exceeds size limit");
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > maxBytes) {
            throw new IOException("MySQL CDC Schema History checkpoint exceeds size limit");
        }
        return completePrefix(bytes);
    }

    /**
     * Keeps complete newline-terminated history records and discards a possibly partial tail.
     * Debezium FileSchemaHistory appends one JSON document per line and always writes a newline
     * after each completed record. This also sanitizes checkpoints created during an old append
     * race without changing the serialized checkpoint format.
     */
    public static byte[] completePrefix(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            return new byte[0];
        }
        if (bytes[bytes.length - 1] == '\n') {
            return bytes.clone();
        }
        for (int index = bytes.length - 1; index >= 0; index--) {
            if (bytes[index] == '\n') {
                return Arrays.copyOf(bytes, index + 1);
            }
        }
        return new byte[0];
    }
}
