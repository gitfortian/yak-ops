package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/** Closed set of typed JDBC chunk scalars; never deserializes arbitrary Java objects. */
public final class ChunkRangeStateSerializer {
    private static final int MAX_DECIMAL_LENGTH = 1024;

    private ChunkRangeStateSerializer() {}

    public static void write(DataOutputStream out, ChunkRange range) throws IOException {
        out.writeBoolean(range != null);
        if (range == null) return;
        out.writeUTF(range.tableId().catalog() == null ? "" : range.tableId().catalog());
        out.writeUTF(range.tableId().table());
        out.writeUTF(range.column());
        out.writeUTF(range.type().name());
        scalar(out, range.type(), range.start());
        scalar(out, range.type(), range.end());
    }

    public static ChunkRange read(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return null;
        TableId table = new TableId(in.readUTF(), null, in.readUTF());
        String name = in.readUTF();
        String typeName = in.readUTF();
        try {
            LogicalTypeRoot type = LogicalTypeRoot.valueOf(typeName);
            return new ChunkRange(table, name, type, scalar(in, type), scalar(in, type));
        } catch (IllegalArgumentException error) {
            throw new IOException("Corrupt typed snapshot chunk range", error);
        }
    }

    private static void scalar(DataOutputStream out, LogicalTypeRoot type, Object value) throws IOException {
        out.writeBoolean(value != null);
        if (value == null) return;
        switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> {
                if (!(value instanceof Long number)) throw new IOException("Invalid integer chunk scalar");
                out.writeLong(number);
            }
            case DECIMAL -> {
                if (!(value instanceof BigDecimal decimal)) throw new IOException("Invalid decimal chunk scalar");
                String text = decimal.toString();
                if (text.length() > MAX_DECIMAL_LENGTH) throw new IOException("Decimal chunk scalar too large");
                out.writeUTF(text);
            }
            case CHAR, VARCHAR -> {
                if (!(value instanceof String text)) throw new IOException("Invalid string chunk scalar");
                out.writeUTF(text);
            }
            default -> throw new IOException("Unsupported snapshot chunk type: " + type);
        }
    }

    private static Object scalar(DataInputStream in, LogicalTypeRoot type) throws IOException {
        if (!in.readBoolean()) return null;
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> in.readLong();
            case DECIMAL -> {
                String text = in.readUTF();
                if (text.length() > MAX_DECIMAL_LENGTH) throw new IOException("Decimal chunk scalar too large");
                try {
                    yield new BigDecimal(text);
                } catch (NumberFormatException error) {
                    throw new IOException("Corrupt decimal chunk scalar", error);
                }
            }
            case CHAR, VARCHAR -> in.readUTF();
            default -> throw new IOException("Unsupported snapshot chunk type: " + type);
        };
    }
}
