package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Shared enumerator state serialization; keeps historic INITIAL and LATEST checkpoint formats readable. */
public final class PendingSplitsStateSerializer implements SimpleVersionedSerializer<PendingSplitsState> {
    private static final int VERSION = 5;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final MySqlHybridEnumeratorStateSerializer hybrid = new MySqlHybridEnumeratorStateSerializer();
    private final MySqlPendingSplitsStateSerializer stream = new MySqlPendingSplitsStateSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(PendingSplitsState state) throws IOException {
        final int kind;
        final byte[] payload;
        if (state instanceof MySqlHybridEnumeratorState initial) {
            kind = 2;
            payload = hybrid.serialize(initial);
        } else if (state instanceof MySqlPendingSplitsState latest) {
            kind = 1;
            payload = stream.serialize(latest);
        } else {
            throw new IOException("Unsupported MySQL pending state");
        }
        if (payload.length > MAX_BYTES - 5) throw new IOException("Oversized MySQL enumerator state");
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            out.writeByte(kind);
            out.writeInt(payload.length);
            out.write(payload);
            return buffer.toByteArray();
        }
    }

    @Override
    public PendingSplitsState deserialize(int version, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Invalid state bytes");
        if (version == 1) return stream.deserialize(1, bytes);
        if (version == 4) return hybrid.deserialize(4, bytes);
        if (version != VERSION) throw new IOException("Unsupported pending splits state version: " + version);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int kind = in.readUnsignedByte();
            int length = in.readInt();
            if (length < 0 || length > in.available() || length > MAX_BYTES) {
                throw new IOException("Invalid pending state length");
            }
            byte[] payload = in.readNBytes(length);
            if (in.available() != 0) throw new IOException("Trailing pending state bytes");
            return switch (kind) {
                case 1 -> stream.deserialize(stream.getVersion(), payload);
                case 2 -> hybrid.deserialize(hybrid.getVersion(), payload);
                default -> throw new IOException("Unknown MySQL pending state kind " + kind);
            };
        } catch (RuntimeException error) {
            throw new IOException("Invalid MySQL pending state", error);
        }
    }
}
