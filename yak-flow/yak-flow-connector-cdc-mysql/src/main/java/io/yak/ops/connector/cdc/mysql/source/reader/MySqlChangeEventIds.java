package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Derives stable opaque change identities from Debezium's exact source event/row coordinates. */
final class MySqlChangeEventIds {
    private MySqlChangeEventIds() {}

    /**
     * Returns null when the position cannot distinguish multiple changes in one Binlog event.
     *
     * <p>Legacy keyed CDC remains valid without identities. Future RETRACT must require
     * a non-null identity; guessing a row number from an incomplete offset is unsafe.
     */
    static String fromBinlog(DebeziumOffset offset) {
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
