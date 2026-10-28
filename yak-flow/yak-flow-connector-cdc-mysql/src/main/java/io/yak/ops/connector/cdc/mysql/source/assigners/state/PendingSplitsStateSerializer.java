package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.ChunkRangeStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Serializes enumerator state in one versioned envelope shared by the MySQL split assigners.
 *
 * <p>The split serializer version and the kind of pending splits state are included in the
 * envelope, as in Flink CDC. Reader-owned binlog offsets are not duplicated in the
 * assigned-binlog state. A binlog split returned to the enumerator remains checkpointed
 * while pending, so an interrupted latest-offset reader cannot accidentally seek to NOW.
 *
 * <p>The on-disk format intentionally differs from earlier YakFlow nested enumerator
 * frames. Old checkpoints are rejected rather than reinterpreted.
 */
public final class PendingSplitsStateSerializer implements SimpleVersionedSerializer<PendingSplitsState> {
    private static final int VERSION = 9;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SPLITS = 10_000;
    // Match Flink CDC's state discriminators. Snapshot-only is reserved for future use.
    private static final int BINLOG_PENDING_SPLITS_STATE_FLAG = 2;
    private static final int HYBRID_PENDING_SPLITS_STATE_FLAG = 3;

    private final SimpleVersionedSerializer<MySqlSplit> splitSerializer;

    public PendingSplitsStateSerializer(SimpleVersionedSerializer<MySqlSplit> splitSerializer) {
        this.splitSerializer = Objects.requireNonNull(splitSerializer, "splitSerializer");
    }

    public PendingSplitsStateSerializer() {
        this(MySqlSplitSerializer.INSTANCE);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(PendingSplitsState state) throws IOException {
        Objects.requireNonNull(state, "state");
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            out.writeInt(splitSerializer.getVersion());
            if (state instanceof BinlogPendingSplitsState binlog) {
                out.writeInt(BINLOG_PENDING_SPLITS_STATE_FLAG);
                writeBinlog(out, binlog);
            } else if (state instanceof HybridPendingSplitsState hybrid) {
                out.writeInt(HYBRID_PENDING_SPLITS_STATE_FLAG);
                writeHybrid(out, hybrid);
            } else {
                throw new IOException(
                        "Unsupported pending splits state: " + state.getClass().getName());
            }
            out.flush();
            if (buffer.size() > MAX_BYTES) {
                throw new IOException("Oversized MySQL enumerator checkpoint");
            }
            return buffer.toByteArray();
        }
    }

    @Override
    public PendingSplitsState deserialize(int version, byte[] bytes) throws IOException {
        if (version != VERSION || bytes == null || bytes.length < Integer.BYTES * 2 || bytes.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL pending splits checkpoint format: " + version);
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int splitVersion = in.readInt();
            if (splitVersion != splitSerializer.getVersion()) {
                throw new IOException("Unsupported nested MySQL split serializer version: " + splitVersion);
            }
            int kind = in.readInt();
            PendingSplitsState state =
                    switch (kind) {
                        case BINLOG_PENDING_SPLITS_STATE_FLAG -> readBinlog(in, splitVersion);
                        case HYBRID_PENDING_SPLITS_STATE_FLAG -> readHybrid(in, splitVersion);
                        default -> throw new IOException("Unknown MySQL pending splits state kind: " + kind);
                    };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL pending splits state");
            }
            return state;
        } catch (RuntimeException error) {
            throw new IOException("Corrupted MySQL enumerator checkpoint", error);
        }
    }

    private void writeBinlog(DataOutputStream out, BinlogPendingSplitsState state) throws IOException {
        out.writeBoolean(state.splitAssigned());
        if (!state.splitAssigned()) {
            writeSplit(out, state.pendingSplit());
        }
    }

    private BinlogPendingSplitsState readBinlog(DataInputStream in, int splitVersion) throws IOException {
        boolean assigned = in.readBoolean();
        MySqlBinlogSplit pending = assigned ? null : (MySqlBinlogSplit) readSplit(in, splitVersion);
        return new BinlogPendingSplitsState(assigned, pending);
    }

    private void writeHybrid(DataOutputStream out, HybridPendingSplitsState state) throws IOException {
        out.writeByte(state.phase().ordinal());
        out.writeBoolean(state.isBinlogSplitAssigned());
        if (state.pendingBinlog() != null) {
            writeSplit(out, state.pendingBinlog());
        }
        writeOffset(out, state.lowWatermark());
        writeOffset(out, state.highWatermark());
        SnapshotPendingSplitsState snapshot = state.getSnapshotPendingSplits();
        out.writeBoolean(snapshot.snapshotPlanned());
        out.writeInt(snapshot.totalSnapshotSplits());
        writeSplits(out, snapshot.getRemainingSplits());
        out.writeInt(snapshot.getAssignedSplits().size());
        for (var entry : snapshot.getAssignedSplits().entrySet()) {
            out.writeUTF(entry.getKey());
            writeSplit(out, entry.getValue());
        }
        out.writeInt(snapshot.getFinishedSplits().size());
        for (String id : snapshot.getFinishedSplits()) {
            out.writeUTF(id);
        }
        MySqlSplitSerializer.writeFinishedSplits(out, snapshot.getFinishedSplitInfos());
        out.writeInt(snapshot.nextTableIndex());
        out.writeInt(snapshot.nextChunkIndex());
        ChunkRangeStateSerializer.write(out, snapshot.nextChunkRange());
    }

    private HybridPendingSplitsState readHybrid(DataInputStream in, int splitVersion) throws IOException {
        int phaseIndex = in.readUnsignedByte();
        if (phaseIndex >= HybridPendingSplitsState.Phase.values().length) {
            throw new IOException("Unknown MySQL hybrid phase");
        }
        boolean binlogAssigned = in.readBoolean();
        MySqlBinlogSplit pending = binlogAssigned ? null : (MySqlBinlogSplit) readSplit(in, splitVersion);
        DebeziumOffset low = readOffset(in);
        DebeziumOffset high = readOffset(in);
        boolean planned = in.readBoolean();
        int total = in.readInt();
        List<MySqlSnapshotSplit> pendingSnapshots = readSplits(in, splitVersion);
        int assignedCount = boundedCount(in);
        Map<String, MySqlSnapshotSplit> assigned = new LinkedHashMap<>();
        for (int i = 0; i < assignedCount; i++) {
            String id = in.readUTF();
            MySqlSnapshotSplit split = (MySqlSnapshotSplit) readSplit(in, splitVersion);
            if (!id.equals(split.splitId()) || assigned.putIfAbsent(id, split) != null) {
                throw new IOException("Corrupt assigned MySQL snapshot split");
            }
        }
        int finishedCount = boundedCount(in);
        Set<String> finished = new LinkedHashSet<>();
        for (int i = 0; i < finishedCount; i++) {
            if (!finished.add(in.readUTF())) {
                throw new IOException("Duplicate finished MySQL snapshot split");
            }
        }
        var finishedInfos = MySqlSplitSerializer.readFinishedSplits(in);
        int nextTable = in.readInt();
        int nextChunk = in.readInt();
        ChunkRange typedCursor = ChunkRangeStateSerializer.read(in);
        SnapshotPendingSplitsState snapshot = new SnapshotPendingSplitsState(
                planned, total, pendingSnapshots, assigned, finished, finishedInfos, nextTable, nextChunk, typedCursor);
        return new HybridPendingSplitsState(
                snapshot, binlogAssigned, pending, HybridPendingSplitsState.Phase.values()[phaseIndex], low, high);
    }

    private void writeSplits(DataOutputStream out, List<MySqlSnapshotSplit> splits) throws IOException {
        if (splits.size() > MAX_SPLITS) {
            throw new IOException("Too many MySQL snapshot splits");
        }
        out.writeInt(splits.size());
        for (MySqlSnapshotSplit split : splits) {
            writeSplit(out, split);
        }
    }

    private List<MySqlSnapshotSplit> readSplits(DataInputStream in, int splitVersion) throws IOException {
        int count = boundedCount(in);
        List<MySqlSnapshotSplit> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add((MySqlSnapshotSplit) readSplit(in, splitVersion));
        }
        return result;
    }

    private void writeSplit(DataOutputStream out, MySqlSplit split) throws IOException {
        byte[] bytes = splitSerializer.serialize(split);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("Invalid nested MySQL split size");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private MySqlSplit readSplit(DataInputStream in, int splitVersion) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_BYTES || length > in.available()) {
            throw new IOException("Invalid nested MySQL split length");
        }
        return splitSerializer.deserialize(splitVersion, in.readNBytes(length));
    }

    private static int boundedCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_SPLITS) {
            throw new IOException("Invalid MySQL split count");
        }
        return count;
    }

    private static void writeOffset(DataOutputStream out, DebeziumOffset offset) throws IOException {
        out.writeBoolean(offset != null);
        if (offset != null) {
            offset.writeTo(out);
        }
    }

    private static DebeziumOffset readOffset(DataInputStream in) throws IOException {
        return in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
    }
}
