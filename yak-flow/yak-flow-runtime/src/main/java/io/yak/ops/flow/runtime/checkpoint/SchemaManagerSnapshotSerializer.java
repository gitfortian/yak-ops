package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.BasicType;
import io.yak.ops.core.types.CharType;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.core.types.UnresolvedDecimalType;
import io.yak.ops.core.types.VarBinaryType;
import io.yak.ops.core.types.VarCharType;
import io.yak.ops.core.types.BinaryType;
import io.yak.ops.core.types.ZonedTimestampType;
import io.yak.ops.flow.runtime.schema.SchemaManagerSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable, bounded binary encoding for runtime schema state; Java object serialization is not used. */
public final class SchemaManagerSnapshotSerializer implements SimpleVersionedSerializer<SchemaManagerSnapshot> {

    private static final int MAGIC = 0x59414B53;
    private static final int VERSION = 1;
    private static final int MAX_TABLES = 10_000;
    private static final int MAX_COLUMNS = 10_000;
    private static final int MAX_REPLAY_EVENTS = 256;
    private static final int MAX_STRING_BYTES = 60_000;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public byte[] serialize(SchemaManagerSnapshot snapshot) throws IOException {
        if (snapshot.tables().size() > MAX_TABLES) {
            throw new IOException("Schema state exceeds the table limit");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(snapshot.tables().size());
            for (SchemaManagerSnapshot.TableState table : snapshot.tables()) {
                writeTableId(out, table.sourceTable());
                writeTableId(out, table.targetTable());
                writeSchema(out, table.sourceSchema());
                writeSchema(out, table.targetSchema());
                out.writeLong(table.version());
                if (table.appliedEventFingerprints().size() > MAX_REPLAY_EVENTS) {
                    throw new IOException("Schema replay history exceeds the limit");
                }
                out.writeInt(table.appliedEventFingerprints().size());
                for (var entry : table.appliedEventFingerprints().entrySet()) {
                    writeString(out, entry.getKey());
                    writeString(out, entry.getValue());
                }
            }
            out.flush();
            return bytes.toByteArray();
        }
    }

    @Override
    public SchemaManagerSnapshot deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION) {
            throw new IOException("Unsupported schema manager snapshot version: " + version);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(serialized))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("Invalid schema manager snapshot header");
            }
            int count = bounded(in.readInt(), MAX_TABLES, "table count");
            List<SchemaManagerSnapshot.TableState> tables = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                TableId source = readTableId(in);
                TableId target = readTableId(in);
                Schema sourceSchema = readSchema(in);
                Schema targetSchema = readSchema(in);
                long schemaVersion = in.readLong();
                int eventCount = bounded(in.readInt(), MAX_REPLAY_EVENTS, "replay event count");
                Map<String, String> applied = new LinkedHashMap<>();
                for (int event = 0; event < eventCount; event++) {
                    String eventId = readString(in);
                    String fingerprint = readString(in);
                    if (applied.putIfAbsent(eventId, fingerprint) != null) {
                        throw new IOException("Duplicate schema replay event ID");
                    }
                }
                tables.add(new SchemaManagerSnapshot.TableState(
                        source, target, sourceSchema, targetSchema, schemaVersion, applied));
            }
            if (in.available() != 0) {
                throw new IOException("Schema manager snapshot contains trailing bytes");
            }
            return new SchemaManagerSnapshot(tables);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid schema manager snapshot contents", failure);
        }
    }

    private static void writeTableId(DataOutputStream out, TableId table) throws IOException {
        writeNullableString(out, table.catalog());
        writeNullableString(out, table.schema());
        writeString(out, table.table());
    }

    private static TableId readTableId(DataInputStream in) throws IOException {
        return new TableId(readNullableString(in), readNullableString(in), readString(in));
    }

    private static void writeSchema(DataOutputStream out, Schema schema) throws IOException {
        writeNullableString(out, schema.comment());
        out.writeInt(schema.columns().size());
        if (schema.columns().size() > MAX_COLUMNS) {
            throw new IOException("Schema exceeds the column limit");
        }
        for (PhysicalColumn column : schema.columns()) {
            writeString(out, column.name());
            writeString(out, column.type().asSerializableString());
            writeNullableString(out, column.comment());
            writeProperties(out, column.properties());
        }
        out.writeInt(schema.primaryKeys().size());
        for (String key : schema.primaryKeys()) {
            writeString(out, key);
        }
    }

    private static Schema readSchema(DataInputStream in) throws IOException {
        String comment = readNullableString(in);
        int count = bounded(in.readInt(), MAX_COLUMNS, "column count");
        if (count == 0) {
            throw new IOException("Schema has no columns");
        }
        List<PhysicalColumn> columns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String name = readString(in);
            LogicalType type = parseType(readString(in));
            String columnComment = readNullableString(in);
            ColumnProperties properties = readProperties(in);
            columns.add(new PhysicalColumn(name, type, columnComment, properties));
        }
        int keyCount = bounded(in.readInt(), count, "primary key count");
        List<String> keys = new ArrayList<>(keyCount);
        for (int index = 0; index < keyCount; index++) {
            keys.add(readString(in));
        }
        return new Schema(columns, keys, comment);
    }

    private static void writeProperties(DataOutputStream out, ColumnProperties properties) throws IOException {
        out.writeBoolean(properties != null);
        if (properties == null) return;
        writeNullableString(out, properties.defaultExpression());
        writeNullableBoolean(out, properties.autoIncrement());
        writeNullableBoolean(out, properties.generated());
        writeNullableString(out, properties.onUpdateExpression());
        writeNullableBoolean(out, properties.defaultGenerated());
        writeNullableString(out, properties.nativeType());
    }

    private static ColumnProperties readProperties(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return null;
        return new ColumnProperties(
                readNullableString(in),
                readNullableBoolean(in),
                readNullableBoolean(in),
                readNullableString(in),
                readNullableBoolean(in),
                readNullableString(in));
    }

    private static void writeNullableBoolean(DataOutputStream out, Boolean value) throws IOException {
        out.writeByte(value == null ? -1 : value ? 1 : 0);
    }

    private static Boolean readNullableBoolean(DataInputStream in) throws IOException {
        return switch (in.readByte()) {
            case -1 -> null;
            case 0 -> false;
            case 1 -> true;
            default -> throw new IOException("Invalid nullable boolean");
        };
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) writeString(out, value);
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? readString(in) : null;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        if (value == null || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
            throw new IOException("Schema state string is null or too large");
        }
        out.writeUTF(value);
    }

    private static String readString(DataInputStream in) throws IOException {
        return in.readUTF();
    }

    private static int bounded(int value, int maximum, String name) throws IOException {
        if (value < 0 || value > maximum) {
            throw new IOException("Invalid schema state " + name);
        }
        return value;
    }

    private static LogicalType parseType(String serialized) throws IOException {
        String type = serialized.trim();
        boolean nullable = !type.endsWith(" NOT NULL");
        if (!nullable) type = type.substring(0, type.length() - " NOT NULL".length()).trim();

        Matcher unresolved = Pattern.compile("UNRESOLVED_DECIMAL\\((\\?|\\d+),\\s*(\\?|\\d+)\\)")
                .matcher(type);
        if (unresolved.matches()) {
            Integer precision = unresolved.group(1).equals("?") ? null : Integer.valueOf(unresolved.group(1));
            Integer scale = unresolved.group(2).equals("?") ? null : Integer.valueOf(unresolved.group(2));
            return new UnresolvedDecimalType(nullable, precision, scale);
        }
        Matcher decimal = Pattern.compile("DECIMAL\\((\\d+),\\s*(\\d+)\\)").matcher(type);
        if (decimal.matches()) {
            return new DecimalType(nullable, Integer.parseInt(decimal.group(1)), Integer.parseInt(decimal.group(2)));
        }
        Matcher charType = Pattern.compile("CHAR\\((\\d+)\\)").matcher(type);
        if (charType.matches()) return new CharType(nullable, Integer.parseInt(charType.group(1)));
        Matcher varchar = Pattern.compile("VARCHAR\\((\\d+)\\)").matcher(type);
        if (varchar.matches()) return new VarCharType(nullable, Integer.parseInt(varchar.group(1)));
        Matcher binary = Pattern.compile("BINARY\\((\\d+)\\)").matcher(type);
        if (binary.matches()) return new BinaryType(nullable, Integer.parseInt(binary.group(1)));
        Matcher varbinary = Pattern.compile("VARBINARY\\((\\d+)\\)").matcher(type);
        if (varbinary.matches()) return new VarBinaryType(nullable, Integer.parseInt(varbinary.group(1)));
        Matcher time = Pattern.compile("TIME\\((\\d+)\\)").matcher(type);
        if (time.matches()) return new TimeType(nullable, Integer.parseInt(time.group(1)));
        Matcher zoned = Pattern.compile("TIMESTAMP\\((\\d+)\\) WITH TIME ZONE").matcher(type);
        if (zoned.matches()) return new ZonedTimestampType(nullable, Integer.parseInt(zoned.group(1)));
        Matcher timestamp = Pattern.compile("TIMESTAMP\\((\\d+)\\)").matcher(type);
        if (timestamp.matches()) return new TimestampType(nullable, Integer.parseInt(timestamp.group(1)));
        if (type.equals("STRING")) return new VarCharType(nullable, VarCharType.MAX_LENGTH);
        if (type.equals("BYTES")) return new VarBinaryType(nullable, VarBinaryType.MAX_LENGTH);
        String rootName = type.equals("INT") ? "INTEGER" : type;
        try {
            return new BasicType(nullable, LogicalTypeRoot.valueOf(rootName));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unsupported logical type in schema manager snapshot", failure);
        }
    }
}
