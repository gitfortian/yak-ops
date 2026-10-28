package io.yak.ops.connector.cdc.mysql.debezium;

import java.io.IOException;
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

    private DebeziumSchemaHistorySnapshot() {}

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
