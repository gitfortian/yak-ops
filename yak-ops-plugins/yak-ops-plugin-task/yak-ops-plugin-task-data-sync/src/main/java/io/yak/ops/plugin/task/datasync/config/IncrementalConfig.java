package io.yak.ops.plugin.task.datasync.config;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Non-secret, per-table JDBC incremental selection. Cursor progress is never stored in task parameters. */
public record IncrementalConfig(
        Mode mode, String column, String initialValue, long delaySeconds, long lookbackSeconds) {
    public enum Mode {
        TIMESTAMP,
        INCREASING
    }

    public IncrementalConfig {
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
