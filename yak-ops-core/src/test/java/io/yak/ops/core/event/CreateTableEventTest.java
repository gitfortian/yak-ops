package io.yak.ops.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateTableEventTest {

    private static final TableId TABLE = new TableId("source", null, "orders.with.dot");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false)), new Column("name", LogicalTypes.varchar(32))),
            List.of("id"));

    @Test
    void copyPreservesSchemaAndCommentsWithoutChangingOriginalIdentity() {
        CreateTableEvent original = new CreateTableEvent(TABLE, SCHEMA, "Orders", Map.of("name", "Display name"));
        TableId target = new TableId("target", "public", "orders.with.dot");
        CreateTableEvent copy = original.copy(target);
        assertEquals(TABLE, original.tableId());
        assertEquals(target, copy.tableId());
        assertSame(SCHEMA, copy.getSchema());
        assertEquals(original.getTableComment(), copy.getTableComment());
        assertEquals(original.getColumnComments(), copy.getColumnComments());
        assertNotEquals(original, copy);
        assertEquals(original, original.copy(TABLE));
        assertEquals(original.hashCode(), original.copy(TABLE).hashCode());
        assertEquals(SchemaChangeEventType.CREATE_TABLE, copy.getType());
    }

    @Test
    void commentsAreDetachedValidatedAndExcludedFromDiagnostics() {
        Map<String, String> comments = new HashMap<>();
        comments.put("name", "comment-sensitive-value");
        CreateTableEvent event = new CreateTableEvent(TABLE, SCHEMA, "table-sensitive-value", comments);
        comments.clear();
        assertEquals("comment-sensitive-value", event.getColumnComments().get("name"));
        assertThrows(UnsupportedOperationException.class, () -> event.getColumnComments().clear());
        assertThrows(IllegalArgumentException.class, () -> new CreateTableEvent(TABLE, SCHEMA, null, Map.of("bad", "x")));
        assertFalse(event.toString().contains("sensitive-value"));
    }

    @Test
    void eventAndExistingValueTypesRoundTripThroughJavaSerialization() throws Exception {
        CreateTableEvent event = new CreateTableEvent(TABLE, SCHEMA, "Orders", Map.of("name", "Name"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(event);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(event, input.readObject());
        }
    }

    @Test
    void allOperationTagsRoundTripWithoutAdvertisingConnectorSupport() {
        for (SchemaChangeEventType type : SchemaChangeEventType.values()) {
            assertEquals(type, SchemaChangeEventType.ofTag(type.getTag()));
        }
        assertThrows(IllegalArgumentException.class, () -> SchemaChangeEventType.ofTag("unknown"));
        assertEquals(SchemaChangeEventType.CREATE_TABLE, SchemaChangeEventType.ofEvent(new CreateTableEvent(TABLE, SCHEMA)));
        MetadataApplier applier = event -> {
            throw new SchemaEvolveException(event, "Unsupported operation");
        };
        assertTrue(applier.getSupportedSchemaEvolutionTypes().isEmpty());
        assertFalse(applier.acceptsSchemaEvolutionType(SchemaChangeEventType.CREATE_TABLE));
        assertThrows(
                UnsupportedOperationException.class,
                () -> applier.setAcceptedSchemaEvolutionTypes(Set.of(SchemaChangeEventType.CREATE_TABLE)));
    }

    @Test
    void failuresPreserveTheEventAndOriginalCause() {
        CreateTableEvent event = new CreateTableEvent(TABLE, SCHEMA);
        Exception cause = new Exception("original failure");
        SchemaEvolveException failure = new SchemaEvolveException(event, "Schema preparation failed", cause);
        assertSame(event, failure.getApplyingEvent());
        assertSame(cause, failure.getCause());
        assertEquals("Schema preparation failed", failure.getMessage());
        assertEquals(failure.getMessage(), failure.getExceptionMessage());
        assertFalse(failure.toString().contains("original failure"));
    }
}
