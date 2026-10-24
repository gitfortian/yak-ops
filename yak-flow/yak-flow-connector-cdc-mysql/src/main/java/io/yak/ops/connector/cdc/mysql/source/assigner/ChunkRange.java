package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One typed snapshot range with database-ordered, half-open boundaries.
 *
 * <p>Values are immutable JDBC scalars. They are not compared using Java string ordering:
 * MySQL collations define the ordering used by the snapshot predicate and the planner.
 * Typed boundaries are persisted in the new checkpoint format. The legacy
 * BIGINT-only JDBC reader remains unavailable to typed chunks until execution integration.
 */
public record ChunkRange(TableId tableId, String column, LogicalTypeRoot type, Object start, Object end) {
    public ChunkRange {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(type, "type");
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Snapshot chunk column must not be blank");
        }
        switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT -> {
                require(start, Long.class);
                require(end, Long.class);
                if (start != null && end != null && ((Long) start) >= ((Long) end)) {
                    throw new IllegalArgumentException("Numeric snapshot chunk boundaries must advance");
                }
            }
            case DECIMAL -> {
                require(start, BigDecimal.class);
                require(end, BigDecimal.class);
                if (start != null && end != null && ((BigDecimal) start).compareTo((BigDecimal) end) >= 0) {
                    throw new IllegalArgumentException("Decimal snapshot chunk boundaries must advance");
                }
            }
            case CHAR, VARCHAR -> {
                require(start, String.class);
                require(end, String.class);
            }
            default -> throw new IllegalArgumentException("Unsupported MySQL snapshot chunk type: " + type);
        }
    }

    private static void require(Object value, Class<?> type) {
        if (value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException("Snapshot chunk boundary has incompatible scalar type");
        }
    }

    public boolean last() {
        return end == null;
    }

    @Override
    public String toString() {
        return "ChunkRange{table=" + tableId + ", column=" + column + ", type=" + type + ", values=redacted}";
    }
}
