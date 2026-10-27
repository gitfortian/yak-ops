package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A serializer for the {@link MySqlSplit}.
 *
 * <p>Snapshot and binlog splits share one entry point, as in Flink CDC. The schema-history
 * and checkpointed normalized snapshot output remain part of YakFlow's existing recovery
 * protocol until the corresponding snapshot reader and Debezium engine are migrated.
 */
public final class MySqlSplitSerializer implements SimpleVersionedSerializer<MySqlSplit> {

    public static final MySqlSplitSerializer INSTANCE = new MySqlSplitSerializer();

    // An incompatible layout: old nested hybrid/binlog frames must not be silently restored.
    private static final int VERSION = 7;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int SNAPSHOT_SPLIT_FLAG = 1;
    private static final int BINLOG_SPLIT_FLAG = 2;
    private static final int HYBRID_BINLOG_SPLIT_FLAG = 3;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        try (var buffer = new ByteArrayOutputStream();
                var out = new DataOutputStream(buffer)) {
            if (split.isSnapshotSplit()) {
                out.writeInt(SNAPSHOT_SPLIT_FLAG);
                writeSnapshot(out, split.asSnapshotSplit());
            } else if (split instanceof MySqlHybridBinlogSplit hybrid) {
                out.writeInt(HYBRID_BINLOG_SPLIT_FLAG);
                out.writeByte(hybrid.phase().ordinal());
                out.writeBoolean(hybrid.highWatermark() != null);
                if (hybrid.highWatermark() != null) {
                    hybrid.highWatermark().writeTo(out);
                }
                writeBinlog(out, hybrid.binlog());
                writeFinishedSplits(out, hybrid.finishedSplits());
            } else if (split.isBinlogSplit()) {
                out.writeInt(BINLOG_SPLIT_FLAG);
                writeBinlog(out, split.asBinlogSplit());
            } else {
                throw new IOException("Unknown MySQL split: " + split.getClass().getName());
            }
            out.flush();
            if (buffer.size() > MAX_BYTES) {
                throw new IOException("MySQL split checkpoint exceeds size limit");
            }
            return buffer.toByteArray();
        }
    }

    @Override
    public MySqlSplit deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION || serialized == null || serialized.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL split checkpoint version or size: " + version);
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            MySqlSplit split = switch (in.readInt()) {
                case SNAPSHOT_SPLIT_FLAG -> readSnapshot(in);
                case BINLOG_SPLIT_FLAG -> readBinlog(in);
                case HYBRID_BINLOG_SPLIT_FLAG -> readHybridBinlog(in);
                default -> throw new IOException("Unknown MySQL split kind");
            };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL split checkpoint");
            }
            return split;
        } catch (RuntimeException error) {
            throw new IOException("Invalid MySQL split checkpoint", error);
        }
    }

    private static void writeSnapshot(DataOutputStream out, MySqlSnapshotSplit split) throws IOException {
        out.writeUTF(split.splitId());
        out.writeUTF(split.fingerprint());
        out.writeUTF(split.tableId().catalog() == null ? "" : split.tableId().catalog());
        out.writeUTF(split.tableId().table());
        writeLong(out, split.lowerInclusive());
        writeLong(out, split.upperExclusive());
        writeLong(out, split.lastEmittedKey());
        out.writeByte(split.finished() != null ? 2 : split.output() != null ? 1 : 0);
        if (split.output() != null) {
            MySqlSnapshotOutputSerializer.writeCompact(out, split.output());
            out.writeInt(split.outputPosition());
        } else if (split.finished() != null) {
            split.finished().highWatermark().writeTo(out);
        }
        ChunkRangeStateSerializer.write(out, split.chunkRange());
    }

    private static MySqlSnapshotSplit readSnapshot(DataInputStream in) throws IOException {
        String id = in.readUTF();
        String fingerprint = in.readUTF();
        TableId table = new TableId(in.readUTF(), null, in.readUTF());
        Long lower = readLong(in);
        Long upper = readLong(in);
        Long key = readLong(in);
        int stage = in.readUnsignedByte();
        MySqlSnapshotOutput output = null;
        FinishedSnapshotSplitInfo finished = null;
        DebeziumOffset high = null;
        int position = 0;
        if (stage == 1) {
            output = MySqlSnapshotOutputSerializer.readCompact(in, table, true);
            position = in.readInt();
        } else if (stage == 2) {
            high = DebeziumOffset.readFrom(in);
        } else if (stage != 0) {
            throw new IOException("Unknown MySQL snapshot checkpoint stage");
        }
        ChunkRange range = ChunkRangeStateSerializer.read(in);
        if (high != null) {
            finished = new FinishedSnapshotSplitInfo(table, id, lower, upper, high, range);
        }
        return new MySqlSnapshotSplit(id, fingerprint, table, lower, upper, key, output, position, finished, range);
    }

    private static void writeBinlog(DataOutputStream out, MySqlBinlogSplit split) throws IOException {
        out.writeUTF(split.definitionFingerprint());
        out.writeBoolean(split.offset() != null);
        if (split.offset() != null) {
            split.offset().writeTo(out);
        }
        byte[] history = split.schemaHistory();
        if (history.length > MAX_BYTES) {
            throw new IOException("MySQL schema history exceeds checkpoint limit");
        }
        out.writeInt(history.length);
        out.write(history);
    }

    private static MySqlBinlogSplit readBinlog(DataInputStream in) throws IOException {
        String fingerprint = in.readUTF();
        DebeziumOffset offset = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
        int length = in.readInt();
        if (length < 0 || length > in.available() || length > MAX_BYTES) {
            throw new IOException("Invalid MySQL schema history length");
        }
        byte[] history = in.readNBytes(length);
        if (offset != null && history.length == 0) {
            throw new IOException("Restored MySQL binlog offset is missing schema history");
        }
        return new MySqlBinlogSplit(fingerprint, offset, history);
    }

    private static MySqlHybridBinlogSplit readHybridBinlog(DataInputStream in) throws IOException {
        int phase = in.readUnsignedByte();
        if (phase >= MySqlHybridBinlogSplit.Phase.values().length) {
            throw new IOException("Unknown MySQL hybrid binlog phase");
        }
        DebeziumOffset high = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
        MySqlBinlogSplit binlog = readBinlog(in);
        return new MySqlHybridBinlogSplit(
                binlog, MySqlHybridBinlogSplit.Phase.values()[phase], high, readFinishedSplits(in));
    }

    /** Serializes completed snapshot chunk watermarks for reader and enumerator checkpoints. */
    public static void writeFinishedSplits(DataOutputStream out, List<FinishedSnapshotSplitInfo> finished)
            throws IOException {
        if (finished.size() > 10_000) {
            throw new IOException("Too many finished MySQL snapshot splits");
        }
        out.writeInt(finished.size());
        for (FinishedSnapshotSplitInfo info : finished) {
            out.writeUTF(info.tableId().catalog() == null ? "" : info.tableId().catalog());
            out.writeUTF(info.tableId().table());
            out.writeUTF(info.splitId());
            writeLong(out, info.lowerInclusive());
            writeLong(out, info.upperExclusive());
            info.highWatermark().writeTo(out);
            ChunkRangeStateSerializer.write(out, info.chunkRange());
        }
    }

    public static List<FinishedSnapshotSplitInfo> readFinishedSplits(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 10_000 || size > in.available()) {
            throw new IOException("Invalid finished MySQL snapshot split count");
        }
        List<FinishedSnapshotSplitInfo> result = new ArrayList<>(size);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < size; i++) {
            TableId table = new TableId(in.readUTF(), null, in.readUTF());
            String id = in.readUTF();
            if (!ids.add(id)) {
                throw new IOException("Duplicate finished MySQL snapshot split: " + id);
            }
            Long lower = readLong(in);
            Long upper = readLong(in);
            DebeziumOffset high = DebeziumOffset.readFrom(in);
            ChunkRange range = ChunkRangeStateSerializer.read(in);
            result.add(new FinishedSnapshotSplitInfo(table, id, lower, upper, high, range));
        }
        return List.copyOf(result);
    }

    private static void writeLong(DataOutputStream out, Long value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value);
        }
    }

    private static Long readLong(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readLong() : null;
    }
}
