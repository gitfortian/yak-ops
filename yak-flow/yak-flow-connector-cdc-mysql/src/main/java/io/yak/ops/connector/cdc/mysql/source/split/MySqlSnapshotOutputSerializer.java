package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Scalar-only snapshot state encoding; never enables Java or polymorphic object deserialization. */
final class MySqlSnapshotOutputSerializer {
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private MySqlSnapshotOutputSerializer() {}

    static void write(DataOutputStream out, MySqlSnapshotOutput output) throws IOException {
        output.low().writeTo(out);
        output.high().writeTo(out);
        out.writeInt(output.rows().size());
        for (TableRecord record : output.rows()) {
            int arity = record.row().getArity();
            if (arity > 4096) throw new IOException("Snapshot row arity exceeds state limit");
            out.writeInt(arity);
            for (int i = 0; i < arity; i++) writeValue(out, record.row().getField(i));
            if (out.size() > MAX_BYTES) throw new IOException("Snapshot output exceeds state byte limit");
        }
    }

    static MySqlSnapshotOutput read(DataInputStream in, TableId table) throws IOException {
        BinlogOffset low = BinlogOffset.readFrom(in);
        BinlogOffset high = BinlogOffset.readFrom(in);
        int count = count(in, 100_000);
        List<TableRecord> rows = new ArrayList<>(Math.min(count, 1024));
        for (int n = 0; n < count; n++) {
            int arity = count(in, 4096);
            GenericRowData row = new GenericRowData(arity);
            for (int i = 0; i < arity; i++) row.setField(i, readValue(in));
            rows.add(new TableRecord(table, RowKind.INSERT, row));
        }
        return new MySqlSnapshotOutput(low, high, rows);
    }

    /**
     * Stores prepared rows in a bounded, checksummed frame.
     *
     * <p>These rows must remain durable until the Reader finishes emission: recreating a newer
     * snapshot after partial external writes could leave deleted rows in a JDBC target. Compression
     * reduces checkpoint size without changing that recovery contract.
     */
    static void writeCompact(DataOutputStream out, MySqlSnapshotOutput output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream state = new DataOutputStream(bytes)) {
            write(state, output);
        }
        byte[] uncompressed = bytes.toByteArray();
        if (uncompressed.length > MAX_BYTES) {
            throw new IOException("Snapshot output exceeds uncompressed state byte limit");
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream compressor =
                new DeflaterOutputStream(compressed, new Deflater(Deflater.BEST_SPEED))) {
            compressor.write(uncompressed);
        }
        byte[] zipped = compressed.toByteArray();
        boolean useCompression = zipped.length < uncompressed.length;
        byte[] stored = useCompression ? zipped : uncompressed;
        CRC32C checksum = new CRC32C();
        checksum.update(uncompressed);
        out.writeInt(uncompressed.length);
        out.writeBoolean(useCompression);
        out.writeInt(stored.length);
        out.writeInt((int) checksum.getValue());
        out.write(stored);
    }

    /** Reads the version 3 frame, rejecting oversized, truncated or corrupted snapshot state. */
    static MySqlSnapshotOutput readCompact(DataInputStream in, TableId table) throws IOException {
        int originalSize = in.readInt();
        boolean compressed = in.readBoolean();
        int storedSize = in.readInt();
        int checksum = in.readInt();
        if (originalSize < 1
                || originalSize > MAX_BYTES
                || storedSize < 1
                || storedSize > MAX_BYTES
                || storedSize > in.available()) {
            throw new IOException("Invalid snapshot output frame size");
        }
        byte[] stored = in.readNBytes(storedSize);
        byte[] decoded;
        if (compressed) {
            decoded = new byte[originalSize];
            try (InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(stored))) {
                int copied = 0;
                while (copied < decoded.length) {
                    int read = stream.read(decoded, copied, decoded.length - copied);
                    if (read < 0) throw new IOException("Truncated compressed snapshot output");
                    if (read == 0) throw new IOException("Snapshot inflater did not advance");
                    copied += read;
                }
                if (stream.read() != -1) {
                    throw new IOException("Compressed snapshot exceeds declared size");
                }
            }
        } else {
            decoded = stored;
        }
        if (decoded.length != originalSize) throw new IOException("Snapshot output size differs from frame");
        CRC32C actual = new CRC32C();
        actual.update(decoded);
        if ((int) actual.getValue() != checksum) throw new IOException("Snapshot output checksum mismatch");
        try (DataInputStream source = new DataInputStream(new ByteArrayInputStream(decoded))) {
            MySqlSnapshotOutput restored = read(source, table);
            if (source.available() != 0) throw new IOException("Trailing bytes in snapshot output");
            return restored;
        }
    }

    private static void writeValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte(0);
        } else if (value instanceof Boolean v) {
            out.writeByte(1);
            out.writeBoolean(v);
        } else if (value instanceof Byte v) {
            out.writeByte(2);
            out.writeByte(v);
        } else if (value instanceof Short v) {
            out.writeByte(3);
            out.writeShort(v);
        } else if (value instanceof Integer v) {
            out.writeByte(4);
            out.writeInt(v);
        } else if (value instanceof Long v) {
            out.writeByte(5);
            out.writeLong(v);
        } else if (value instanceof Float v) {
            out.writeByte(6);
            out.writeFloat(v);
        } else if (value instanceof Double v) {
            out.writeByte(7);
            out.writeDouble(v);
        } else if (value instanceof BigDecimal v) {
            out.writeByte(8);
            text(out, v.toString());
        } else if (value instanceof String v) {
            out.writeByte(9);
            text(out, v);
        } else if (value instanceof byte[] v) {
            out.writeByte(10);
            bytes(out, v);
        } else if (value instanceof LocalDate v) {
            out.writeByte(11);
            text(out, v.toString());
        } else if (value instanceof LocalTime v) {
            out.writeByte(12);
            text(out, v.toString());
        } else if (value instanceof LocalDateTime v) {
            out.writeByte(13);
            text(out, v.toString());
        } else if (value instanceof OffsetDateTime v) {
            out.writeByte(14);
            text(out, v.toString());
        } else throw new IOException("Unsupported normalized snapshot scalar type");
    }

    private static Object readValue(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> in.readBoolean();
            case 2 -> in.readByte();
            case 3 -> in.readShort();
            case 4 -> in.readInt();
            case 5 -> in.readLong();
            case 6 -> in.readFloat();
            case 7 -> in.readDouble();
            case 8 -> new BigDecimal(text(in));
            case 9 -> text(in);
            case 10 -> bytes(in);
            case 11 -> LocalDate.parse(text(in));
            case 12 -> LocalTime.parse(text(in));
            case 13 -> LocalDateTime.parse(text(in));
            case 14 -> OffsetDateTime.parse(text(in));
            default -> throw new IOException("Unknown normalized snapshot scalar marker");
        };
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max || count > in.available()) throw new IOException("Invalid snapshot state length");
        return count;
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        if (value.length() > MAX_BYTES) throw new IOException("Snapshot string exceeds state limit");
        bytes(out, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(DataInputStream in) throws IOException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes(in)))
                .toString();
    }

    private static void bytes(DataOutputStream out, byte[] value) throws IOException {
        if (value.length > MAX_BYTES) throw new IOException("Snapshot scalar exceeds state limit");
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] bytes(DataInputStream in) throws IOException {
        return in.readNBytes(count(in, MAX_BYTES));
    }
}
