package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
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
 * Source-only columns may be ignored; target columns must all be mapped. Supported lossless
 * type changes and nullable or capacity changes must satisfy the JDBC write contract. This
 * validation is shared by Definition validation and runtime planning.
 */
public final class JdbcSinkSchemaValidator {

    private JdbcSinkSchemaValidator() {}

    /**
     * Checks the field mapping and primary-key requirements of the selected write mode.
     *
     * @param source source table schema
     * @param target target table schema
     * @param mode Sink write intent and its row identity requirements
     * @throws IllegalArgumentException when the current JDBC Sink cannot write the projected rows
     */
    public static void validate(TableSchema source, TableSchema target, JdbcWriteMode mode) {
        Objects.requireNonNull(mode, "mode");
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
                throw new IllegalArgumentException(
                        "DATA_SYNC target column has no source mapping: " + targetColumn.name());
            }
            if (!JdbcColumnValueConverter.supports(sourceColumn, targetColumn)) {
                throw new IllegalArgumentException(
                        "DATA_SYNC source and target column types are incompatible: " + targetColumn.name());
            }
        }
        if (mode == JdbcWriteMode.RETRACT) validateExactRow(source, target);
        if (mode == JdbcWriteMode.UPSERT) {
            Set<String> sourceKeys = new HashSet<>(source.primaryKeys());
            if (sourceKeys.isEmpty()) {
                throw new IllegalArgumentException("JDBC changelog UPSERT requires a source write identity; "
                        + "a snapshot Chunk Key does not identify a unique row");
            }
            if (target.primaryKeys().isEmpty()) {
                throw new IllegalArgumentException("JDBC changelog UPSERT requires a target write identity");
            }
            if (!sourceKeys.equals(new HashSet<>(target.primaryKeys()))) {
                throw new IllegalArgumentException(
                        "JDBC UPSERT requires matching source and target write identity columns");
            }
        }
    }

    /** An exact-row DELETE matches full before-images, never a Snapshot Chunk Key. */
    public static void validateExactRow(TableSchema source, TableSchema target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!source.primaryKeys().isEmpty() || !target.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException(
                    "JDBC RETRACT requires source and target schemas without physical primary keys");
        }
        if (source.columnCount() == 0 || source.columnCount() != target.columnCount()) {
            throw new IllegalArgumentException("JDBC RETRACT requires a complete full-row field mapping");
        }
        for (int index = 0; index < source.columnCount(); index++) {
            Column from = source.column(index);
            Column to = target.column(index);
            if (!from.name().equals(to.name())
                    || !from.dataType().isResolved()
                    || !to.dataType().isResolved()
                    || !from.dataType().equals(to.dataType())
                    || (from.nullable() && !to.nullable())) {
                throw new IllegalArgumentException(
                        "JDBC RETRACT requires equal source and target column definitions: " + to.name());
            }
            LogicalTypeRoot type = from.dataType().getTypeRoot();
            if (type != LogicalTypeRoot.BOOLEAN
                    && type != LogicalTypeRoot.TINYINT
                    && type != LogicalTypeRoot.SMALLINT
                    && type != LogicalTypeRoot.INTEGER
                    && type != LogicalTypeRoot.BIGINT
                    && type != LogicalTypeRoot.DECIMAL
                    && type != LogicalTypeRoot.BINARY
                    && type != LogicalTypeRoot.VARBINARY) {
                throw new IllegalArgumentException(
                        "JDBC RETRACT full-row comparison requires verified database semantics for: " + to.name());
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
