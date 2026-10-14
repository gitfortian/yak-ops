package io.yak.ops.connector.jdbc.source;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Objects;

/** Frozen, non-secret cursor bounds for one bounded JDBC scan, independent of split-key bounds. */
public record JdbcIncrementalRange(Mode mode, String column, String lower, String upper) {
    public enum Mode { TIMESTAMP, INCREASING }

    public JdbcIncrementalRange {
        Objects.requireNonNull(mode, "mode");
        if (column == null || column.isBlank() || column.length() > 128) {
            throw new IllegalArgumentException("An incremental range requires a column");
        }
        if (upper == null || upper.isBlank()) throw new IllegalArgumentException("An incremental range requires an upper bound");
        if (mode == Mode.TIMESTAMP) {
            LocalDateTime.parse(upper);
            if (lower != null) LocalDateTime.parse(lower);
            if (lower != null && LocalDateTime.parse(lower).isAfter(LocalDateTime.parse(upper)))
                throw new IllegalArgumentException("Decreasing timestamp range");
        } else {
            new BigInteger(upper);
            if (lower != null && new BigInteger(lower).compareTo(new BigInteger(upper)) > 0)
                throw new IllegalArgumentException("Decreasing numeric range");
        }
    }

    /** Frozen lower value, or no lower limit for a safe initial full window. */
    public Object lowerParameter() {
        return lower == null ? null : mode == Mode.TIMESTAMP ? LocalDateTime.parse(lower) : new java.math.BigDecimal(lower);
    }

    /** Frozen upper bound, never recalculated during checkpoint recovery. */
    public Object upperParameter() {
        return mode == Mode.TIMESTAMP ? LocalDateTime.parse(upper) : new java.math.BigDecimal(upper);
    }
}
