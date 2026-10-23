package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * One source schema transition and its already-planned target changes.
 *
 * <p>The event ID must be stable across replay (for example, a source log position plus table
 * identity). The source connector supplies the before/after source schema; planning supplies the
 * before/after target schema and the ordered, validated target events. The runtime never guesses
 * a target mapping or translates arbitrary SQL.
 */
public record SchemaChangeRequest(
        String eventId,
        TableId sourceTable,
        TableId targetTable,
        Schema sourceSchemaBefore,
        Schema sourceSchemaAfter,
        Schema targetSchemaBefore,
        Schema targetSchemaAfter,
        List<SchemaChangeEvent> targetChanges) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SchemaChangeRequest {
        eventId = Objects.requireNonNull(eventId, "eventId").trim();
        sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        targetTable = Objects.requireNonNull(targetTable, "targetTable");
        sourceSchemaBefore = Objects.requireNonNull(sourceSchemaBefore, "sourceSchemaBefore");
        sourceSchemaAfter = Objects.requireNonNull(sourceSchemaAfter, "sourceSchemaAfter");
        targetSchemaBefore = Objects.requireNonNull(targetSchemaBefore, "targetSchemaBefore");
        targetSchemaAfter = Objects.requireNonNull(targetSchemaAfter, "targetSchemaAfter");
        targetChanges = List.copyOf(Objects.requireNonNull(targetChanges, "targetChanges"));
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("Schema change event ID must not be blank");
        }
        if (sourceSchemaBefore.equals(sourceSchemaAfter)) {
            throw new IllegalArgumentException("Schema change must change the source schema");
        }
        if (targetSchemaBefore.equals(targetSchemaAfter) || targetChanges.isEmpty()) {
            throw new IllegalArgumentException("Schema change must include a target schema transition");
        }
        for (SchemaChangeEvent change : targetChanges) {
            if (!targetTable.equals(Objects.requireNonNull(change, "target change").tableId())) {
                throw new IllegalArgumentException("Target schema event identity does not match the request");
            }
        }
    }

    /** Diagnostics expose identity and operation count, never SQL or default expressions. */
    @Override
    public String toString() {
        return "SchemaChangeRequest{eventId='" + eventId + "', sourceTable=" + sourceTable
                + ", targetTable=" + targetTable + ", targetChangeCount=" + targetChanges.size() + "}";
    }
}
