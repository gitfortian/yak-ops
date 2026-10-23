package io.yak.ops.core.schema;

import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.types.LogicalType;
import java.io.Serializable;
import java.util.Objects;

/** An ordered physical column with optional comments and source column metadata. */
public record PhysicalColumn(String name, LogicalType type, String comment, ColumnProperties properties)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    public PhysicalColumn {
        name = Objects.requireNonNull(name, "name");
        type = Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Physical column name must not be blank");
        }
    }

    public PhysicalColumn(String name, LogicalType type) {
        this(name, type, null, null);
    }

    public PhysicalColumn(String name, LogicalType type, String comment) {
        this(name, type, comment, null);
    }

    /** Copies a column with a different type while preserving its physical metadata. */
    public PhysicalColumn copy(LogicalType newType) {
        return new PhysicalColumn(name, newType, comment, properties);
    }

    /** Copies a column with a different identifier while preserving its physical metadata. */
    public PhysicalColumn copy(String newName) {
        return new PhysicalColumn(newName, type, comment, properties);
    }

    /** Diagnostics must not include native expressions or user-provided comments. */
    @Override
    public String toString() {
        return "PhysicalColumn{name='" + name + "', type=" + type + "}";
    }
}
