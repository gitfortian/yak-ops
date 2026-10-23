package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A managed, non-key column removal. Expected original target types fence against deleting
 * an unrelated column after a conflicting external schema modification.
 */
public record DropColumnEvent(
        TableId tableId, List<String> droppedColumnNames, Map<String, LogicalType> expectedColumnTypes)
        implements SchemaChangeEvent {

    public DropColumnEvent {
        tableId = Objects.requireNonNull(tableId, "tableId");
        droppedColumnNames = List.copyOf(Objects.requireNonNull(droppedColumnNames, "droppedColumnNames"));
        expectedColumnTypes = Map.copyOf(Objects.requireNonNull(expectedColumnTypes, "expectedColumnTypes"));
        if (droppedColumnNames.isEmpty()
                || droppedColumnNames.stream().anyMatch(name -> name == null || name.isBlank())
                || new HashSet<>(droppedColumnNames).size() != droppedColumnNames.size()
                || !expectedColumnTypes.keySet().equals(new HashSet<>(droppedColumnNames))
                || expectedColumnTypes.values().stream().anyMatch(type -> !type.isResolved())) {
            throw new IllegalArgumentException("DROP COLUMN requires distinct managed fields and their original types");
        }
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.DROP_COLUMN;
    }

    @Override
    public DropColumnEvent copy(TableId targetTable) {
        return new DropColumnEvent(targetTable, droppedColumnNames, expectedColumnTypes);
    }
}
