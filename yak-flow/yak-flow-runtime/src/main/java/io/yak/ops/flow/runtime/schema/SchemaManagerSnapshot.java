package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, checkpointable schema versions. In-flight changes are deliberately not snapshot state. */
public record SchemaManagerSnapshot(List<TableState> tables) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SchemaManagerSnapshot {
        tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        java.util.Set<TableId> targets = new java.util.HashSet<>();
        for (TableState table : tables) {
            Objects.requireNonNull(table, "table state");
            if (!targets.add(table.targetTable())) {
                throw new IllegalArgumentException("Duplicate target schema state");
            }
        }
    }

    /** One target route's source/target schemas and the replay identities already committed. */
    public record TableState(
            TableId sourceTable,
            TableId targetTable,
            Schema sourceSchema,
            Schema targetSchema,
            long version,
            Map<String, String> appliedEventFingerprints) implements Serializable {

        private static final long serialVersionUID = 1L;

        public TableState {
            sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
            targetTable = Objects.requireNonNull(targetTable, "targetTable");
            sourceSchema = Objects.requireNonNull(sourceSchema, "sourceSchema");
            targetSchema = Objects.requireNonNull(targetSchema, "targetSchema");
            appliedEventFingerprints = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(Objects.requireNonNull(
                            appliedEventFingerprints, "appliedEventFingerprints")));
            if (version < 0 || appliedEventFingerprints.size() > 256) {
                throw new IllegalArgumentException("Invalid schema version or replay history");
            }
            if (appliedEventFingerprints.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue().isBlank())) {
                throw new IllegalArgumentException("Invalid schema replay identity");
            }
        }
    }
}
