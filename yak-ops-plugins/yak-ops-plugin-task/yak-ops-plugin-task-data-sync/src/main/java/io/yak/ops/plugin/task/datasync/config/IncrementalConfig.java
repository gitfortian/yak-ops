package io.yak.ops.plugin.task.datasync.config;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Non-secret, per-table JDBC incremental selection. Cursor progress is never stored in task parameters. */
public final class IncrementalConfig {
    private final Mode mode;
    private final String column;
    private final String initialValue;
    private final long delaySeconds;
    private final long lookbackSeconds;

    @JsonCreator
    public IncrementalConfig(@JsonProperty("mode") Mode mode, @JsonProperty("column") String column, @JsonProperty("initialValue") String initialValue, @JsonProperty("delaySeconds") long delaySeconds, @JsonProperty("lookbackSeconds") long lookbackSeconds) {
        Objects.requireNonNull(mode, "mode");
        if (column == null || column.isBlank() || column.length() > 128) {
        throw new IllegalArgumentException("Incremental column must name an existing table column");
        }
        if (delaySeconds < 0 || delaySeconds > 86_400 || lookbackSeconds < 0 || lookbackSeconds > 604_800) {
        throw new IllegalArgumentException("Invalid incremental delay or lookback");
        }
        if (mode == Mode.INCREASING && (delaySeconds != 0 || lookbackSeconds != 0)) {
        throw new IllegalArgumentException("Increasing cursors do not use timestamp delay or lookback");
        }
        if (initialValue != null && initialValue.length() > 64) {
        throw new IllegalArgumentException("Incremental initial cursor is too long");
        }
        if (initialValue != null && !initialValue.isBlank()) {
        try {
        if (mode == Mode.TIMESTAMP) java.time.LocalDateTime.parse(initialValue);
        else new java.math.BigInteger(initialValue);
        } catch (RuntimeException failure) {
        throw new IllegalArgumentException("Invalid incremental initial cursor", failure);
        }
        }
        this.mode = mode;
        this.column = column;
        this.initialValue = initialValue;
        this.delaySeconds = delaySeconds;
        this.lookbackSeconds = lookbackSeconds;
    }

    @JsonProperty("mode")
    public Mode mode() {
        return mode;
    }

    @JsonProperty("column")
    public String column() {
        return column;
    }

    @JsonProperty("initialValue")
    public String initialValue() {
        return initialValue;
    }

    @JsonProperty("delaySeconds")
    public long delaySeconds() {
        return delaySeconds;
    }

    @JsonProperty("lookbackSeconds")
    public long lookbackSeconds() {
        return lookbackSeconds;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof IncrementalConfig other)) return false;
        return java.util.Objects.equals(mode, other.mode) && java.util.Objects.equals(column, other.column) && java.util.Objects.equals(initialValue, other.initialValue) && java.util.Objects.equals(delaySeconds, other.delaySeconds) && java.util.Objects.equals(lookbackSeconds, other.lookbackSeconds);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(mode, column, initialValue, delaySeconds, lookbackSeconds);
    }

    public enum Mode {
        TIMESTAMP,
        INCREASING
    }

    

    /** Strictly parses a mapping's explicit incremental settings without arbitrary SQL. */
    public static IncrementalConfig from(Map<?, ?> values) {
        if (!Set.of("mode", "column", "initialValue", "delaySeconds", "lookbackSeconds")
                .containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unknown incremental setting");
        }
        Object mode = values.get("mode");
        Object column = values.get("column");
        if (!(mode instanceof String kind) || !(column instanceof String name)) {
            throw new IllegalArgumentException("Incremental mode and column are required");
        }
        Object initial = values.get("initialValue");
        if (initial != null && !(initial instanceof String)) {
            throw new IllegalArgumentException("Incremental initial value must be text");
        }
        return new IncrementalConfig(
                Mode.valueOf(kind),
                name,
                (String) initial,
                seconds(values.get("delaySeconds")),
                seconds(values.get("lookbackSeconds")));
    }

    private static long seconds(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Incremental seconds must be numeric");
        long result = number.longValue();
        if (number.doubleValue() != (double) result)
            throw new IllegalArgumentException("Incremental seconds must be integral");
        return result;
    }
}
