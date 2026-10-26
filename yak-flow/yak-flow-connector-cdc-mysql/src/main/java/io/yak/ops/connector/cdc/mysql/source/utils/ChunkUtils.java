package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Selects a snapshot chunk column without imposing the implementation's current BIGINT limits. */
public final class ChunkUtils {
    private ChunkUtils() {}

    /**
     * Current Hybrid execution admits numeric Chunk Keys for tables without physical primary keys.
     * The generic selector itself remains type-independent for typed SQL range planning.
     */
    public static Column requireSupportedSnapshot(TableSchema schema, String configuredChunkKey) {
        Column selected = getChunkKeyColumn(schema, configuredChunkKey);
        if (schema.primaryKeys().isEmpty()) {
            for (Column column : schema.columns()) {
                if (!column.dataType().isResolved()) {
                    throw new IllegalArgumentException("MySQL CDC requires resolved source column types");
                }
            }
            LogicalTypeRoot type = selected.dataType().getTypeRoot();
            if (type != LogicalTypeRoot.TINYINT
                    && type != LogicalTypeRoot.SMALLINT
                    && type != LogicalTypeRoot.INTEGER
                    && type != LogicalTypeRoot.BIGINT
                    && type != LogicalTypeRoot.DECIMAL) {
                throw new IllegalArgumentException(
                        "Snapshot Chunk Key without a physical primary key must be numeric: " + selected.name());
            }
        }
        return selected;
    }

    /**
     * Uses the explicitly configured field, otherwise the first physical primary-key column.
     * Keyless tables need an explicit field; key uniqueness is not a chunk-selection requirement.
     */
    public static Column getChunkKeyColumn(TableSchema schema, String configuredChunkKey) {
        Objects.requireNonNull(schema, "schema");
        String columnName = configuredChunkKey;
        if (columnName == null) {
            if (schema.primaryKeys().isEmpty()) {
                throw new IllegalArgumentException(
                        "MySQL incremental snapshot without a primary key requires a chunk key column");
            }
            columnName = schema.primaryKeys().getFirst();
        }
        if (columnName.isBlank()) {
            throw new IllegalArgumentException("MySQL snapshot chunk key column must not be blank");
        }
        for (Column column : schema.columns()) {
            if (column.name().equals(columnName)) {
                if (!column.dataType().isResolved() || column.nullable()) {
                    throw new IllegalArgumentException(
                            "MySQL snapshot chunk key column must be resolved and non-null: " + columnName);
                }
                return column;
            }
        }
        throw new IllegalArgumentException("Unknown MySQL snapshot chunk key column: " + columnName);
    }
}
