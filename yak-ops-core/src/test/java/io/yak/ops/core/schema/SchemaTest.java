package io.yak.ops.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaTest {

    private static final TableSchema TABLE = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false)), new Column("name", LogicalTypes.varchar(64))),
            List.of("id"));

    @Test
    void roundTripsAllExplicitPhysicalMetadataWithoutChangingTheRowSchema() {
        ColumnProperties properties = new ColumnProperties("private-default-expression", false, false, null, true, "varchar(64)");
        Schema schema = Schema.from(
                TABLE, "private-table-comment", Map.of("name", "private-column-comment"), Map.of("name", properties));

        assertEquals(TABLE, schema.toTableSchema());
        assertEquals(List.of("id", "name"), schema.columns().stream().map(PhysicalColumn::name).toList());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertEquals("private-table-comment", schema.comment());
        assertEquals(Map.of("name", "private-column-comment"), schema.columnComments());
        assertEquals(Map.of("name", properties), schema.columnProperties());
        assertFalse(schema.toString().contains("private-default-expression"));
        assertFalse(schema.toString().contains("private-table-comment"));
        assertFalse(schema.columns().get(1).toString().contains("private-column-comment"));
    }

    @Test
    void collectionInputsAreDetachedAndSchemaCanNotBeChangedViaAccessors() {
        List<PhysicalColumn> columns = new ArrayList<>(List.of(
                new PhysicalColumn("id", LogicalTypes.BIGINT.copy(false)),
                new PhysicalColumn("name", LogicalTypes.varchar(10))));
        List<String> keys = new ArrayList<>(List.of("id"));
        Schema schema = new Schema(columns, keys, null);
        columns.clear();
        keys.clear();

        assertEquals(2, schema.columns().size());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertThrows(UnsupportedOperationException.class, () -> schema.columns().clear());
        assertThrows(UnsupportedOperationException.class, () -> schema.primaryKeys().clear());
        assertEquals("name", schema.column("name").orElseThrow().name());
        assertTrue(schema.column("absent").isEmpty());
        assertNotEquals(schema, schema.withColumns(List.of(new PhysicalColumn("other", LogicalTypes.INTEGER))));
    }

    @Test
    void rejectsMalformedSchemasAndStaleColumnMetadata() {
        PhysicalColumn id = new PhysicalColumn("id", LogicalTypes.BIGINT);
        assertThrows(IllegalArgumentException.class, () -> new PhysicalColumn("", LogicalTypes.BIGINT));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(id, id), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(id), List.of("other")));
        assertThrows(IllegalArgumentException.class, () -> new Schema(List.of(id), List.of("id", "id")));
        assertThrows(
                IllegalArgumentException.class,
                () -> Schema.from(TABLE, null, Map.of("absent", "invalid"), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> Schema.from(TABLE, null, Map.of(), Map.of("absent", new ColumnProperties(null, false, false))));
    }

    @Test
    void serializationPreservesFieldsAndImmutableViews() throws Exception {
        Map<String, ColumnProperties> attributes = new HashMap<>();
        attributes.put("id", new ColumnProperties(null, true, false));
        Schema original = Schema.from(TABLE, "table comment", Map.of("name", "column comment"), attributes);
        attributes.clear();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(original);
        }
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Schema restored = (Schema) stream.readObject();
            assertEquals(original, restored);
            assertEquals(TABLE, restored.toTableSchema());
            assertThrows(UnsupportedOperationException.class, () -> restored.columns().clear());
        }
    }
}
