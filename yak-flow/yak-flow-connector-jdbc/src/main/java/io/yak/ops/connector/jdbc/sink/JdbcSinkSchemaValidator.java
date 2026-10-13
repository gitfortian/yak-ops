package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates the executable JDBC target projection for a single-table pipeline.
 *
 * <p>The current JDBC Sink binds each target column to a source column with the same exact name.
 * Source-only columns may be ignored; target columns must all be mapped. Logical type roots must
 * match, and nullable or capacity changes must also satisfy the JDBC write contract. This
 * validation is shared by Definition validation and runtime planning.
 */
public final class JdbcSinkSchemaValidator {

    private JdbcSinkSchemaValidator() {}

    /**
     * Checks the field mapping and primary-key requirements of the selected write mode.
     *
     * @param source source table schema
     * @param target target table schema
     * @param requireKeys whether the Sink consumes key-aware changes
     * @throws IllegalArgumentException when the current JDBC Sink cannot write the projected rows
     */
    public static void validate(TableSchema source, TableSchema target, boolean requireKeys) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Map<String, Column> sourceColumns = columns(source);
        Map<String, Column> targetColumns = columns(target);

        for (Column column : sourceColumns.values()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("DATA_SYNC source contains an unresolved logical type");
            }
        }
        for (Column targetColumn : targetColumns.values()) {
            Column sourceColumn = sourceColumns.get(targetColumn.name());
            if (sourceColumn == null) {
                throw new IllegalArgumentException("DATA_SYNC target column has no source mapping: " + targetColumn.name());
            }
            if (!targetColumn.dataType().isResolved()
                    || sourceColumn.dataType().getTypeRoot()
                            != targetColumn.dataType().getTypeRoot()
                    || !JdbcSchemaCompatibility.isCompatible(sourceColumn, targetColumn)) {
                throw new IllegalArgumentException(
                        "DATA_SYNC source and target column types are incompatible: " + targetColumn.name());
            }
        }
        if (requireKeys) {
            Set<String> sourceKeys = new HashSet<>(source.primaryKeys());
            if (sourceKeys.isEmpty() || !sourceKeys.equals(new HashSet<>(target.primaryKeys()))) {
                throw new IllegalArgumentException("UPSERT requires matching source and target primary keys");
            }
        }
    }

    private static Map<String, Column> columns(TableSchema schema) {
        Map<String, Column> result = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (Column column : schema.columns()) {
            if (!names.add(column.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("DATA_SYNC contains ambiguous table column names");
            }
            result.put(column.name(), column);
        }
        return result;
    }
}
