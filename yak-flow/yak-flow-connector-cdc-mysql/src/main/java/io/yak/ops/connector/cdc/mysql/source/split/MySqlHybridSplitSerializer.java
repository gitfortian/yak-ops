package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Versioned transport of snapshot ranges and paused/running Binlog checkpoint state.
 *
 * <p>Version 5 adds counted full-row snapshot images for large duplicate groups.
 * Version 4 records typed ranges; versions 2 through 4 remain readable.
 */
public final class MySqlHybridSplitSerializer implements SimpleVersionedSerializer<MySqlHybridSplit> {

    private static final int VERSION = 5;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final MySqlBinlogSplitSerializer binlogSerializer = new MySqlBinlogSplitSerializer();

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(MySqlHybridSplit split) throws IOException {
        Objects.requireNonNull(split, "split");
        try (var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes)) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                out.writeByte(1);
                out.writeUTF(snapshot.splitId());
                out.writeUTF(snapshot.fingerprint());
                out.writeUTF(
                        snapshot.tableId().catalog() == null
                                ? ""
                                : snapshot.tableId().catalog());
                out.writeUTF(snapshot.tableId().table());
                writeLong(out, snapshot.lowerInclusive());
                writeLong(out, snapshot.upperExclusive());
                writeLong(out, snapshot.lastEmittedKey());
                out.writeByte(snapshot.finished() != null ? 2 : snapshot.output() != null ? 1 : 0);
                if (snapshot.output() != null) {
                    MySqlSnapshotOutputSerializer.writeCompact(out, snapshot.output());
                    out.writeInt(snapshot.outputPosition());
                } else if (snapshot.finished() != null) {
                    snapshot.finished().highWatermark().writeTo(out);
                }
                ChunkRangeStateSerializer.write(out, snapshot.chunkRange());
            } else if (split instanceof MySqlHybridBinlogSplit binlog) {
                out.writeByte(2);
                out.writeByte(binlog.phase().ordinal());
                out.writeBoolean(binlog.highWatermark() != null);
                if (binlog.highWatermark() != null) {
                    binlog.highWatermark().writeTo(out);
                }
                byte[] encoded = binlogSerializer.serialize(binlog.binlog());
                out.writeInt(encoded.length);
                out.write(encoded);
                writeFinishedSplits(out, binlog.finishedSplits());
            } else {
                throw new IOException("Unknown MySQL hybrid split type");
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) {
                throw new IOException("MySQL hybrid split state exceeds size limit");
            }
            return bytes.toByteArray();
        }
    }

    @Override
    public MySqlHybridSplit deserialize(int version, byte[] serialized) throws IOException {
        if ((version != 2 && version != 3 && version != 4 && version != VERSION)
                || serialized == null
                || serialized.length > MAX_BYTES) {
            throw new IOException("Unsupported MySQL hybrid split state version");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            MySqlHybridSplit split =
                    switch (in.readUnsignedByte()) {
                        case 1 -> {
                            String id = in.readUTF();
                            String fingerprint = in.readUTF();
                            TableId table = new TableId(in.readUTF(), null, in.readUTF());
                            Long lower = readLong(in), upper = readLong(in), key = readLong(in);
                            int stage = in.readUnsignedByte();
                            MySqlSnapshotOutput output = null;
                            FinishedSnapshotSplitInfo finished = null;
                            DebeziumOffset finishedHigh = null;
                            int position = 0;
                            if (stage == 1) {
                                output = version == 2
                                        ? MySqlSnapshotOutputSerializer.read(in, table)
                                        : MySqlSnapshotOutputSerializer.readCompact(in, table, version >= 5);
                                position = in.readInt();
                            } else if (stage == 2) {
                                finishedHigh = DebeziumOffset.readFrom(in);
                            } else if (stage != 0) throw new IOException("Unknown snapshot output stage");
                            ChunkRange typed = version >= 4 ? ChunkRangeStateSerializer.read(in) : null;
                            if (finishedHigh != null) {
                                finished = new FinishedSnapshotSplitInfo(table, id, lower, upper, finishedHigh, typed);
                            }
                            yield new MySqlSnapshotSplit(
                                    id, fingerprint, table, lower, upper, key, output, position, finished, typed);
                        }
                        case 2 -> {
                            int phase = in.readUnsignedByte();
                            if (phase >= MySqlHybridBinlogSplit.Phase.values().length) {
                                throw new IOException("Unknown hybrid Binlog phase");
                            }
                            DebeziumOffset high = in.readBoolean() ? DebeziumOffset.readFrom(in) : null;
                            int length = in.readInt();
                            if (length < 0 || length > in.available() || length > MAX_BYTES) {
                                throw new IOException("Invalid hybrid Binlog state size");
                            }
                            yield new MySqlHybridBinlogSplit(
                                    binlogSerializer.deserialize(binlogSerializer.getVersion(), in.readNBytes(length)),
                                    MySqlHybridBinlogSplit.Phase.values()[phase],
                                    high,
                                    readFinishedSplits(in, version >= 4));
                        }
                        default -> throw new IOException("Unknown MySQL hybrid split marker");
                    };
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in MySQL hybrid split state");
            }
            return split;
        } catch (RuntimeException error) {
            throw new IOException("Corrupt MySQL hybrid split state", error);
        }
    }

    public static void writeFinishedSplits(DataOutputStream out, java.util.List<FinishedSnapshotSplitInfo> finished)
            throws IOException {
        if (finished.size() > 10_000) throw new IOException("Too many finished snapshot chunks");
        out.writeInt(finished.size());
        for (FinishedSnapshotSplitInfo info : finished) {
            out.writeUTF(info.tableId().catalog());
            out.writeUTF(info.tableId().table());
            out.writeUTF(info.splitId());
            writeLong(out, info.lowerInclusive());
            writeLong(out, info.upperExclusive());
            info.highWatermark().writeTo(out);
            ChunkRangeStateSerializer.write(out, info.chunkRange());
        }
    }

    public static java.util.List<FinishedSnapshotSplitInfo> readFinishedSplits(DataInputStream in) throws IOException {
        return readFinishedSplits(in, true);
    }

    public static java.util.List<FinishedSnapshotSplitInfo> readFinishedSplits(DataInputStream in, boolean typed)
            throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 10_000 || size > in.available())
            throw new IOException("Invalid finished snapshot count");
        var result = new java.util.ArrayList<FinishedSnapshotSplitInfo>(size);
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < size; i++) {
            TableId table = new TableId(in.readUTF(), null, in.readUTF());
            String id = in.readUTF();
            if (!ids.add(id)) throw new IOException("Duplicate finished snapshot identity");
            Long lower = readLong(in);
            Long upper = readLong(in);
            DebeziumOffset high = DebeziumOffset.readFrom(in);
            ChunkRange range = typed ? ChunkRangeStateSerializer.read(in) : null;
            result.add(new FinishedSnapshotSplitInfo(table, id, lower, upper, high, range));
        }
        return java.util.List.copyOf(result);
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
