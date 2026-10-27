package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Single split serialization entry. Reads historical Hybrid v2-v5 and Binlog-only v1 checkpoints. */
public final class MySqlSplitSerializer implements SimpleVersionedSerializer<MySqlSplit> {
    private static final int VERSION = 6;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final MySqlBinlogSplitSerializer binlog = new MySqlBinlogSplitSerializer();
    private final MySqlHybridSplitSerializer hybrid = new MySqlHybridSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlSplit split) throws IOException {
        final int kind;
        final byte[] payload;
        if (split instanceof MySqlBinlogSplit stream) {
            kind = 1;
            payload = binlog.serialize(stream);
        } else if (split instanceof MySqlHybridSplit staged) {
            kind = 2;
            payload = hybrid.serialize(staged);
        } else {
            throw new IOException("Unknown MySQL split: " + split.getClass().getName());
        }
        if (payload.length > MAX_BYTES - 5) throw new IOException("Oversized MySQL split");
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            out.writeByte(kind);
            out.writeInt(payload.length);
            out.write(payload);
            return buffer.toByteArray();
        }
    }

    @Override
    public MySqlSplit deserialize(int version, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Invalid split bytes");
        if (version == 1) return binlog.deserialize(1, bytes);
        if (version >= 2 && version <= 5) return hybrid.deserialize(version, bytes);
        if (version != VERSION) throw new IOException("Unsupported MySQL split version: " + version);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int kind = in.readUnsignedByte();
            int length = in.readInt();
            if (length < 0 || length > in.available() || length > MAX_BYTES) {
                throw new IOException("Invalid MySQL split payload length");
            }
            byte[] payload = in.readNBytes(length);
            if (in.available() != 0) throw new IOException("Unexpected split bytes");
            return switch (kind) {
                case 1 -> binlog.deserialize(binlog.getVersion(), payload);
                case 2 -> hybrid.deserialize(hybrid.getVersion(), payload);
                default -> throw new IOException("Unknown MySQL split kind " + kind);
            };
        } catch (RuntimeException error) {
            throw new IOException("Invalid MySQL split", error);
        }
    }
}
