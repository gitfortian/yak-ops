package io.yak.ops.connector.cdc.mysql.source.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.jdbc.database.internal.convert.MySqlTypeConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.kafka.connect.data.Struct;

/**
 * Reads Debezium's structured tableChanges metadata, never parses or forwards raw DDL SQL.
 *
 * <p>Only existing-table ADD nullable ordinary columns and same-family safe widening may reach
 * the Sink's JdbcSchemaDerivator. Any unsupported metadata fails closed before an offset advances.
 */
public final class MySqlSchemaChangeConverter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MySqlTypeConverter TYPES = new MySqlTypeConverter();
    private final Set<TableId> selected;

    public MySqlSchemaChangeConverter(Set<TableId> selected) {
        this.selected = Set.copyOf(Objects.requireNonNull(selected, "selected"));
    }

    /**
     * Replays structured table metadata during the initial no_data schema snapshot.
     * Debezium can emit DROP and CREATE for the same existing table while rebuilding
     * history. The final registered set, not any individual operation, is authoritative.
     * These events never enter the runtime SchemaCoordinator.
     */
    public void applyInitialTables(Struct envelope, Set<TableId> registered) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(registered, "registered");
        if (envelope.schema().field("tableChanges") == null
                || !(envelope.get("tableChanges") instanceof List<?> changes)) {
            throw new IllegalStateException("MySQL CDC initial schema snapshot has no tableChanges");
        }
        for (Object item : changes) {
            if (!(item instanceof Struct change)) {
                throw new IllegalStateException("Invalid MySQL CDC initial table metadata");
            }
            TableId table = resolveTable(text(change, "id"));
            if (!selected.contains(table)) {
                continue;
            }
            String operation = text(change, "type");
            if ("DROP".equalsIgnoreCase(operation)) {
                registered.remove(table);
            } else if ("CREATE".equalsIgnoreCase(operation) || "ALTER".equalsIgnoreCase(operation)) {
                if (change.schema().field("table") == null || change.get("table") == null) {
                    throw new IllegalStateException("MySQL CDC initial table metadata is missing: " + table);
                }
                registered.add(table);
            } else {
                throw new IllegalStateException(
                        "Unexpected MySQL CDC table change during initial schema snapshot: " + operation);
            }
        }
    }

    public SourceSchemaChange convert(Struct envelope, DebeziumOffset offset, Map<TableId, Schema> current) {
        Objects.requireNonNull(envelope, "envelope");
        if (envelope.schema().field("tableChanges") == null) {
            throw new UnsupportedOperationException("Debezium DDL has no structured tableChanges metadata");
        }
        Object raw = envelope.get("tableChanges");
        if (!(raw instanceof List<?> changes)) {
            throw new UnsupportedOperationException("Debezium DDL has no table change descriptions");
        }
        // Debezium's structured list can legitimately be empty for database-level DDL
        // that changes no captured table. Only a nonempty table change is actionable;
        // missing or malformed metadata still fails closed without parsing raw SQL.
        if (changes.isEmpty()) {
            return null;
        }
        SourceSchemaChange found = null;
        for (Object item : changes) {
            if (!(item instanceof Struct change)) {
                throw new UnsupportedOperationException("Unsupported Debezium tableChanges encoding");
            }
            String id = text(change, "id");
            TableId table = resolveTable(id);
            if (!selected.contains(table)) {
                continue;
            }
            if (found != null) {
                throw new UnsupportedOperationException("Multi-table MySQL DDL requires explicit per-table ordering");
            }
            String operation = text(change, "type");
            if (!"ALTER".equalsIgnoreCase(operation)) {
                throw new UnsupportedOperationException("MySQL CDC does not support CREATE, DROP or RENAME at runtime");
            }
            Schema previous = Objects.requireNonNull(current.get(table), "Configured CDC table schema is missing");
            Schema evolved = tableSchema(change, previous);
            if (previous.equals(evolved)) {
                throw new UnsupportedOperationException("MySQL DDL changed unsupported physical schema metadata");
            }
            found = new SourceSchemaChange(eventId(offset, table), table, previous, evolved);
        }
        return found;
    }

    private static TableId resolveTable(String id) {
        // Debezium serializes identifiers as quoted catalog.table pairs, e.g.
        // "\"shop\".\"orders\"". A raw String.split retains the quotes and silently
        // treats selected tables as unrelated, dropping standalone DDL events.
        io.debezium.relational.TableId parsed = io.debezium.relational.TableId.parse(id, true);
        if (parsed == null || parsed.catalog() == null || parsed.schema() != null || parsed.table() == null) {
            throw new UnsupportedOperationException("Unsupported Debezium table identity");
        }
        return new TableId(parsed.catalog(), null, parsed.table());
    }

    private static Schema tableSchema(Struct change, Schema previous) {
        io.debezium.relational.history.TableChanges.TableChange parsed = parseTableChange(change);
        if (parsed.getPreviousId() != null) {
            throw new UnsupportedOperationException("MySQL CDC does not support table renames");
        }
        io.debezium.relational.Table table = Objects.requireNonNull(parsed.getTable(), "Debezium table metadata");
        if (!previous.primaryKeys().equals(table.primaryKeyColumnNames())) {
            throw new UnsupportedOperationException("Runtime MySQL CDC cannot change table primary keys");
        }
        List<PhysicalColumn> columns = new ArrayList<>();
        for (io.debezium.relational.Column metadata : table.columns()) {
            String name = metadata.name();
            String nativeType =
                    Objects.requireNonNull(metadata.typeName(), "native type").toLowerCase(java.util.Locale.ROOT);
            if (nativeType.contains("unsigned") || nativeType.startsWith("enum") || nativeType.startsWith("set")) {
                throw new UnsupportedOperationException("Unsupported MySQL native type in CDC DDL");
            }
            if (metadata.isAutoIncremented()
                    || metadata.isGenerated()
                    || metadata.defaultValueExpression().isPresent()) {
                throw new UnsupportedOperationException("CDC schema evolution cannot change generated/default columns");
            }
            LogicalType type;
            try {
                type = TYPES.convertColumn(
                        metadata.jdbcType(),
                        metadata.typeName(),
                        metadata.length(),
                        metadata.scale().orElse(0),
                        metadata.isOptional(),
                        name);
            } catch (SQLException failure) {
                throw new UnsupportedOperationException("Unsupported MySQL CDC column type: " + name, failure);
            }
            PhysicalColumn existing = previous.column(name).orElse(null);
            // Debezium may report only a SQL type family. Never retain a pre-DDL native
            // definition when the logical field has changed.
            columns.add(new PhysicalColumn(
                    name,
                    type,
                    existing == null ? null : existing.comment(),
                    existing != null && existing.type().equals(type) ? existing.properties() : null));
        }
        return new Schema(columns, table.primaryKeyColumnNames(), previous.comment());
    }

    /**
     * Deserializes Debezium's own table-change document rather than independently
     * interpreting its column metadata. The document is captured at the DDL Binlog offset.
     */
    private static io.debezium.relational.history.TableChanges.TableChange parseTableChange(Struct change) {
        try {
            io.debezium.document.Document document = io.debezium.document.Document.create();
            document.setString("type", text(change, "type"));
            document.setString("id", text(change, "id"));
            if (change.schema().field("previousId") != null && change.get("previousId") != null) {
                document.setString("previousId", text(change, "previousId"));
            }
            if (change.schema().field("table") != null && change.get("table") != null) {
                Object raw = change.get("table");
                String json = raw instanceof String string ? string : JSON.writeValueAsString(connectJson(raw));
                document.setDocument(
                        "table",
                        io.debezium.document.DocumentReader.defaultReader().read(json));
            }
            return io.debezium.relational.history.JsonTableChangeSerializer.fromDocument(document, true);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Invalid Debezium structured table metadata", failure);
        }
    }

    /** Converts Kafka Connect's structured metadata into Debezium's document format. */
    private static JsonNode connectJson(Object value) {
        if (value instanceof Struct struct) {
            var result = JSON.createObjectNode();
            for (var field : struct.schema().fields()) {
                result.set(field.name(), connectJson(struct.get(field)));
            }
            return result;
        }
        if (value instanceof List<?> items) {
            var result = JSON.createArrayNode();
            for (Object item : items) {
                result.add(connectJson(item));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            var result = JSON.createObjectNode();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new UnsupportedOperationException("Debezium table metadata has non-string keys");
                }
                result.set(key, connectJson(entry.getValue()));
            }
            return result;
        }
        return JSON.valueToTree(value);
    }

    private static String text(Struct struct, String field) {
        if (struct.schema().field(field) == null) {
            throw new UnsupportedOperationException("Debezium schema change is missing " + field);
        }
        Object value = struct.get(field);
        if (!(value instanceof String result) || result.isBlank()) {
            throw new UnsupportedOperationException("Invalid Debezium schema change " + field);
        }
        return result;
    }

    /** Hashes the complete Connect offset, including intra-binlog event counters. */
    static String eventId(DebeziumOffset offset, TableId table) {
        StringBuilder data = new StringBuilder();
        for (Map<String, Object> part : List.of(offset.partition(), offset.position())) {
            part.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                data.append(entry.getKey().length()).append(':').append(entry.getKey());
                String value = Objects.toString(entry.getValue(), "null");
                data.append(value.length()).append(':').append(value);
            });
        }
        data.append(table.catalog()).append('.').append(table.table());
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
