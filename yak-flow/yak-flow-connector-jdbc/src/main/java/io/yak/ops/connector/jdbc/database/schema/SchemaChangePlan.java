package io.yak.ops.connector.jdbc.database.schema;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.types.LogicalType;
import java.util.List;
import java.util.Objects;

/**
 * Read-only result of comparing a source schema with the current physical target schema.
 *
 * <p>Suggested events do not authorize DDL. A blocked plan must never be partially applied.
 * Database state and available connector capabilities must be rechecked before execution.
 */
public record SchemaChangePlan(TableId tableId, List<Difference> differences, List<SchemaChangeEvent> changes) {

    public SchemaChangePlan {
        tableId = Objects.requireNonNull(tableId, "tableId");
        differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (changes.stream().anyMatch(event -> !tableId.equals(event.tableId()))) {
            throw new IllegalArgumentException("Schema change references a different target table");
        }
        if (hasBlockers(differences) && !changes.isEmpty()) {
            throw new IllegalArgumentException("Blocked schema plan must not propose executable events");
        }
    }

    /** Whether any identified difference prevents safe preparation or supported JDBC row writes. */
    public boolean blocked() {
        return hasBlockers(differences);
    }

    /** Whether structural changes must be applied before the target is ready for new rows. */
    public boolean requiresDdl() {
        return !changes.isEmpty();
    }

    private static boolean hasBlockers(List<Difference> differences) {
        return differences.stream().anyMatch(difference -> difference.kind() == Kind.INCOMPATIBLE);
    }

    /** Structural state of one field or the table key definition. */
    public enum Kind {
        COMPATIBLE,
        CREATE_TABLE,
        ADD_COLUMN,
        ALTER_COLUMN_TYPE,
        METADATA_DIFFERENCE,
        INCOMPATIBLE
    }

    /** Fixed, diagnostic-safe descriptions used by callers without exposing source SQL expressions. */
    public enum Reason {
        COMPATIBLE,
        TABLE_MISSING,
        COLUMN_MISSING,
        COLUMN_WIDENING,
        NULLABILITY_MISMATCH,
        UNSUPPORTED_TYPE,
        UNSAFE_TYPE_CHANGE,
        PRIMARY_KEY_MISMATCH,
        MISSING_KEY_COLUMN,
        COLUMN_ONLY_IN_TARGET,
        AMBIGUOUS_IDENTIFIER,
        UNRESOLVED_TYPE,
        UNSAFE_COLUMN_PROPERTIES,
        COLUMN_METADATA_DIFFERENCE
    }

    /**
     * One difference with canonical logical types. The reason never contains a raw default,
     * database DDL statement or sensitive metadata.
     */
    public record Difference(
            String columnName, Kind kind, Reason reason, LogicalType sourceType, LogicalType targetType) {
        public Difference {
            kind = Objects.requireNonNull(kind, "kind");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }
}
