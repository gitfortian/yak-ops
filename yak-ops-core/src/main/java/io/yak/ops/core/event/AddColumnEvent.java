package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.PhysicalColumn;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Describes ordered insertion of one or more columns into an existing table schema. */
public record AddColumnEvent(TableId tableId, List<ColumnWithPosition> addedColumns) implements SchemaChangeEvent {
    private static final long serialVersionUID = 1L;

    public AddColumnEvent {
        tableId = Objects.requireNonNull(tableId, "tableId");
        addedColumns = List.copyOf(Objects.requireNonNull(addedColumns, "addedColumns"));
        if (addedColumns.isEmpty()) {
            throw new IllegalArgumentException("ADD_COLUMN requires at least one column");
        }
    }

    /** Position relative to an existing or previously inserted column. */
    public enum ColumnPosition {
        FIRST,
        LAST,
        BEFORE,
        AFTER
    }

    /** A column and its requested location in the resulting schema. */
    public record ColumnWithPosition(PhysicalColumn column, ColumnPosition position, String referenceColumn)
            implements Serializable {
        public ColumnWithPosition {
            column = Objects.requireNonNull(column, "column");
            position = Objects.requireNonNull(position, "position");
            boolean relative = position == ColumnPosition.BEFORE || position == ColumnPosition.AFTER;
            if (relative != (referenceColumn != null && !referenceColumn.isBlank())) {
                throw new IllegalArgumentException("Relative column position requires an existing column name");
            }
        }

        public ColumnWithPosition(PhysicalColumn column) {
            this(column, ColumnPosition.LAST, null);
        }

        @Override
        public String toString() {
            return "ColumnWithPosition{name=" + column.name() + ", position=" + position + ", referenceColumn="
                    + referenceColumn + "}";
        }
    }

    public static ColumnWithPosition first(PhysicalColumn column) {
        return new ColumnWithPosition(column, ColumnPosition.FIRST, null);
    }

    public static ColumnWithPosition last(PhysicalColumn column) {
        return new ColumnWithPosition(column);
    }

    public static ColumnWithPosition before(PhysicalColumn column, String referenceColumn) {
        return new ColumnWithPosition(column, ColumnPosition.BEFORE, referenceColumn);
    }

    public static ColumnWithPosition after(PhysicalColumn column, String referenceColumn) {
        return new ColumnWithPosition(column, ColumnPosition.AFTER, referenceColumn);
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.ADD_COLUMN;
    }

    @Override
    public AddColumnEvent copy(TableId newTableId) {
        return new AddColumnEvent(newTableId, addedColumns);
    }

    @Override
    public String toString() {
        return "AddColumnEvent{tableId=" + tableId + ", columnCount=" + addedColumns.size() + "}";
    }
}
