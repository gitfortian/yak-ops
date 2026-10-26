package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Validates the proposed keyless snapshot contract without starting a CDC reader. */
public final class MySqlKeylessSourceContract {
    private MySqlKeylessSourceContract() {}

    public static Column validate(TableSchema schema, String chunkKeyColumn) {
        Objects.requireNonNull(schema, "schema");
        if (!schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Keyless CDC requires a source table without a physical primary key");
        }
        if (chunkKeyColumn == null || chunkKeyColumn.isBlank()) {
            throw new IllegalArgumentException("Keyless CDC requires an explicit snapshot Chunk Key column");
        }
        for (Column column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("Keyless CDC requires resolved source column types");
            }
        }
        Column selected = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumn);
        LogicalTypeRoot type = selected.dataType().getTypeRoot();
        if (type != LogicalTypeRoot.TINYINT
                && type != LogicalTypeRoot.SMALLINT
                && type != LogicalTypeRoot.INTEGER
                && type != LogicalTypeRoot.BIGINT
                && type != LogicalTypeRoot.DECIMAL) {
            throw new IllegalArgumentException(
                    "Initial keyless CDC requires a non-null numeric or DECIMAL Chunk Key: " + selected.name());
        }
        // Stability during snapshot is a runtime requirement: metadata cannot prove immutability.
        return selected;
    }
}
