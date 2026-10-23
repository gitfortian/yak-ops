package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalType;
import java.util.Map;
import java.util.Objects;

/** Describes column type changes with optional original types for stale-schema detection. */
public record AlterColumnTypeEvent(
        TableId tableId, Map<String, LogicalType> typeMapping, Map<String, LogicalType> oldTypeMapping)
        implements SchemaChangeEvent {
    private static final long serialVersionUID = 1L;

    public AlterColumnTypeEvent {
        tableId = Objects.requireNonNull(tableId, "tableId");
        typeMapping = Map.copyOf(Objects.requireNonNull(typeMapping, "typeMapping"));
        oldTypeMapping = Map.copyOf(Objects.requireNonNull(oldTypeMapping, "oldTypeMapping"));
        if (typeMapping.isEmpty()) {
            throw new IllegalArgumentException("ALTER_COLUMN_TYPE requires at least one column");
        }
        if (!typeMapping.keySet().containsAll(oldTypeMapping.keySet())
                || typeMapping.keySet().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Invalid altered column identifiers");
        }
    }

    public AlterColumnTypeEvent(TableId tableId, Map<String, LogicalType> typeMapping) {
        this(tableId, typeMapping, Map.of());
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.ALTER_COLUMN_TYPE;
    }

    @Override
    public AlterColumnTypeEvent copy(TableId newTableId) {
        return new AlterColumnTypeEvent(newTableId, typeMapping, oldTypeMapping);
    }

    @Override
    public String toString() {
        return "AlterColumnTypeEvent{tableId=" + tableId + ", columns=" + typeMapping.keySet() + "}";
    }
}
