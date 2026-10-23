package io.yak.ops.core.schema;

import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, ordered table description for schema change events.
 *
 * <p>Logical type nullability is owned by the column type. Database-specific properties are
 * retained as metadata, not SQL. The row-writing contract remains {@link TableSchema}.
 */
public record Schema(List<PhysicalColumn> columns, List<String> primaryKeys, String comment)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    public Schema {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        primaryKeys = List.copyOf(Objects.requireNonNull(primaryKeys, "primaryKeys"));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Schema must contain columns");
        }
        Set<String> names = new HashSet<>();
        for (PhysicalColumn column : columns) {
            if (!names.add(column.name())) {
                throw new IllegalArgumentException("Duplicate schema column: " + column.name());
            }
        }
        Set<String> keys = new HashSet<>();
        for (String key : primaryKeys) {
            if (key == null || !names.contains(key) || !keys.add(key)) {
                throw new IllegalArgumentException("Invalid schema primary key: " + key);
            }
        }
    }

    public Schema(List<PhysicalColumn> columns, List<String> primaryKeys) {
        this(columns, primaryKeys, null);
    }

    /**
     * Copies a JDBC-facing table description into the schema event model without losing field order
     * or detached column metadata.
     */
    public static Schema from(
            TableSchema table,
            String comment,
            Map<String, String> columnComments,
            Map<String, ColumnProperties> columnProperties) {
        Objects.requireNonNull(table, "table");
        Map<String, String> comments = Map.copyOf(Objects.requireNonNull(columnComments, "columnComments"));
        Map<String, ColumnProperties> properties =
                Map.copyOf(Objects.requireNonNull(columnProperties, "columnProperties"));
        Set<String> names = new HashSet<>();
        List<PhysicalColumn> columns = new ArrayList<>();
        for (Column column : table.columns()) {
            names.add(column.name());
            columns.add(new PhysicalColumn(
                    column.name(), column.dataType(), comments.get(column.name()), properties.get(column.name())));
        }
        if (!names.containsAll(comments.keySet()) || !names.containsAll(properties.keySet())) {
            throw new IllegalArgumentException("Column metadata must reference declared fields");
        }
        return new Schema(columns, table.primaryKeys(), comment);
    }

    /** Returns the ordered, database-independent type shape used by RowData readers and writers. */
    public TableSchema toTableSchema() {
        return new TableSchema(
                columns.stream().map(column -> new Column(column.name(), column.type())).toList(), primaryKeys);
    }

    /** Returns a detached map of explicitly declared comments. */
    public Map<String, String> columnComments() {
        Map<String, String> comments = new LinkedHashMap<>();
        for (PhysicalColumn column : columns) {
            if (column.comment() != null) {
                comments.put(column.name(), column.comment());
            }
        }
        return Map.copyOf(comments);
    }

    /** Returns a detached map of present column metadata, including values of unknown provenance. */
    public Map<String, ColumnProperties> columnProperties() {
        Map<String, ColumnProperties> properties = new LinkedHashMap<>();
        for (PhysicalColumn column : columns) {
            if (column.properties() != null) {
                properties.put(column.name(), column.properties());
            }
        }
        return Map.copyOf(properties);
    }

    /** Looks up a column using its exact identifier. */
    public Optional<PhysicalColumn> column(String name) {
        return columns.stream().filter(column -> column.name().equals(name)).findFirst();
    }

    /** The result retains field order, keys and table comment. */
    public Schema withColumns(List<PhysicalColumn> updated) {
        return new Schema(updated, primaryKeys, comment);
    }

    /** Diagnostics intentionally omit potentially sensitive source expressions and comments. */
    @Override
    public String toString() {
        return "Schema{columns=" + columns.stream().map(PhysicalColumn::name).toList()
                + ", primaryKeys=" + primaryKeys + "}";
    }
}
