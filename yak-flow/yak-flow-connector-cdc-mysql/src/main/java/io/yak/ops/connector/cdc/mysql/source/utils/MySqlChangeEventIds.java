package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Derives stable opaque change identities from Debezium's exact source event/row coordinates. */
public final class MySqlChangeEventIds {
    private MySqlChangeEventIds() {}

    /**
     * Returns null when the position cannot distinguish multiple changes in one Binlog event.
     *
     * <p>Legacy keyed CDC remains valid without identities. Future RETRACT must require
     * a non-null identity; guessing a row number from an incomplete offset is unsafe.
     */
    public static String fromBinlog(DebeziumOffset offset) {
        Map<String, Object> position = offset.position();
        if (!(position.get("file") instanceof String file)
                || file.isBlank()
                || !(position.get("pos") instanceof Number)
                || !(position.get("row") instanceof Number)
                || !(position.get("event") instanceof Number)) {
            return null;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                write(out, offset.partition());
                write(out, position);
            }
            return "binlog:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate MySQL change identity", exception);
        }
    }

    /**
     * An opaque, deterministic identity for a normalized snapshot image.
     *
     * <p>Flink CDC reconciles keyed rows using physical primary keys and keyless rows
     * using their complete images. Snapshot row ordinals cannot identify a physical
     * row after a failed attempt re-scans a changed chunk. Multiple identical
     * keyless images receive distinct occurrence numbers at emission.
     */
    public static String fromSnapshot(TableRecord record, TableSchema schema) {
        if (record.row().getArity() != schema.columnCount()) {
            throw new IllegalArgumentException("Snapshot event image differs from source schema");
        }
        boolean keyed = !schema.primaryKeys().isEmpty();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                if (keyed) {
                    out.writeInt(schema.primaryKeys().size());
                    for (String key : schema.primaryKeys()) {
                        int position = -1;
                        for (int i = 0; i < schema.columnCount(); i++) {
                            if (schema.column(i).name().equals(key)) {
                                position = i;
                                break;
                            }
                        }
                        if (position < 0 || record.row().isNullAt(position)) {
                            throw new IllegalArgumentException("Snapshot primary key is missing or null");
                        }
                        out.writeUTF(key);
                        writeSnapshotValue(out, record.row().getField(position));
                    }
                } else {
                    out.writeInt(schema.columnCount());
                    for (int i = 0; i < schema.columnCount(); i++) {
                        out.writeUTF(schema.column(i).name());
                        writeSnapshotValue(out, record.row().getField(i));
                    }
                }
            }
            return (keyed ? "key:" : "row:")
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate MySQL snapshot identity", exception);
        }
    }

    private static void writeSnapshotValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte(0);
        } else if (value instanceof byte[] bytes) {
            out.writeByte(1);
            out.writeInt(bytes.length);
            out.write(bytes);
        } else if (value instanceof Number number) {
            out.writeByte(2);
            String normalized = number instanceof BigDecimal decimal
                    ? decimal.stripTrailingZeros().toPlainString()
                    : number.toString();
            byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        } else {
            out.writeByte(3);
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private static void write(DataOutputStream out, Map<String, Object> fields) throws IOException {
        out.writeInt(fields.size());
        for (var field : new TreeMap<>(fields).entrySet()) {
            out.writeUTF(field.getKey());
            Object value = field.getValue();
            if (value == null) {
                out.writeByte(0);
            } else if (value instanceof String text) {
                out.writeByte(1);
                out.writeUTF(text);
            } else if (value instanceof Number number) {
                out.writeByte(2);
                // Debezium may deserialize the same integral position as Integer or Long.
                if (number instanceof Float || number instanceof Double) {
                    throw new IOException("Floating point Binlog positions cannot identify changes");
                }
                out.writeUTF(number.toString());
            } else if (value instanceof Boolean flag) {
                out.writeByte(3);
                out.writeBoolean(flag);
            } else {
                throw new IOException("Unsupported MySQL event identity field");
            }
        }
    }
}
