package io.yak.ops.core.schema;

import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies supported schema events to immutable descriptions without executing database DDL. */
public final class SchemaUtils {
    private SchemaUtils() {}

    /**
     * Applies an event and returns an independent schema value.
     *
     * <p>A CREATE_TABLE starts a schema. Other events require an existing schema. Unimplemented
     * event types fail rather than silently treating a schema modification as a no-op.
     */
    public static Schema applySchemaChangeEvent(Schema schema, SchemaChangeEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof CreateTableEvent create) {
            return create.getPhysicalSchema();
        }
        Objects.requireNonNull(schema, "schema");
        if (event instanceof AddColumnEvent add) {
            List<PhysicalColumn> columns = new ArrayList<>(schema.columns());
            for (AddColumnEvent.ColumnWithPosition addition : add.addedColumns()) {
                int index =
                        switch (addition.position()) {
                            case FIRST -> 0;
                            case LAST -> columns.size();
                            case BEFORE -> indexOf(columns, addition.referenceColumn());
                            case AFTER -> indexOf(columns, addition.referenceColumn()) + 1;
                        };
                if (columns.stream()
                        .anyMatch(
                                column -> column.name().equals(addition.column().name()))) {
                    throw new IllegalArgumentException(
                            "Column already exists: " + addition.column().name());
                }
                columns.add(index, addition.column());
            }
            return schema.withColumns(columns);
        }
        if (event instanceof AlterColumnTypeEvent alter) {
            for (var name : alter.typeMapping().keySet()) {
                PhysicalColumn current = schema.column(name)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown column for type change: " + name));
                if (alter.oldTypeMapping().containsKey(name)
                        && !current.type().equals(alter.oldTypeMapping().get(name))) {
                    throw new IllegalArgumentException("Column type changed before applying event: " + name);
                }
            }
            List<PhysicalColumn> columns = schema.columns().stream()
                    .map(column -> alter.typeMapping().containsKey(column.name())
                            ? column.copy(alter.typeMapping().get(column.name()))
                            : column)
                    .toList();
            return schema.withColumns(columns);
        }
        throw new UnsupportedOperationException("Unsupported schema event: " + event.getType());
    }

    private static int indexOf(List<PhysicalColumn> columns, String name) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown reference column: " + name);
    }
}
