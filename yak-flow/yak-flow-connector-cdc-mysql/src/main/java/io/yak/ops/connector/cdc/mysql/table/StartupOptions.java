package io.yak.ops.connector.cdc.mysql.table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable MySQL CDC startup selection, following Flink CDC's StartupOptions ownership.
 *
 * <p>Only INITIAL and LATEST_OFFSET are executable today. Other modes are represented for
 * subsequent implementations but must be rejected before source construction. Runtime
 * Debezium offsets remain owned by BinlogOffset and checkpointed split state, not this object.
 */
public final class StartupOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    public final StartupMode startupMode;

    private StartupOptions(StartupMode startupMode) {
        this.startupMode = Objects.requireNonNull(startupMode, "startupMode");
    }

    public static StartupOptions initial() {
        return new StartupOptions(StartupMode.INITIAL);
    }

    public static StartupOptions latest() {
        return new StartupOptions(StartupMode.LATEST_OFFSET);
    }

    public static StartupOptions of(StartupMode mode) {
        return new StartupOptions(mode);
    }

    /** Parses the established data-sync startup configuration without changing its values. */
    public static StartupOptions from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("MySQL CDC startup mode must not be null");
        }
        StartupMode mode =
                switch (value) {
                    case "initial" -> StartupMode.INITIAL;
                    case "earliest-offset" -> StartupMode.EARLIEST_OFFSET;
                    case "latest-offset" -> StartupMode.LATEST_OFFSET;
                    case "specific-offset" -> StartupMode.SPECIFIC_OFFSETS;
                    case "timestamp" -> StartupMode.TIMESTAMP;
                    case "snapshot" -> StartupMode.SNAPSHOT;
                    default -> throw new IllegalArgumentException("Unsupported startup mode: " + value);
                };
        return of(mode);
    }

    public StartupMode startupMode() {
        return startupMode;
    }

    public String asModeString() {
        return switch (startupMode) {
            case INITIAL -> "initial";
            case EARLIEST_OFFSET -> "earliest-offset";
            case LATEST_OFFSET -> "latest-offset";
            case SPECIFIC_OFFSETS -> "specific-offset";
            case TIMESTAMP -> "timestamp";
            case SNAPSHOT -> "snapshot";
        };
    }

    public boolean isStreamOnly() {
        return switch (startupMode) {
            case EARLIEST_OFFSET, LATEST_OFFSET, SPECIFIC_OFFSETS, TIMESTAMP -> true;
            case INITIAL, SNAPSHOT -> false;
        };
    }

    public boolean isSnapshotOnly() {
        return startupMode == StartupMode.SNAPSHOT;
    }

    /** Fail closed until a mode has its own tested reader and checkpoint semantics. */
    public void requireSupported() {
        if (startupMode != StartupMode.INITIAL && startupMode != StartupMode.LATEST_OFFSET) {
            throw new UnsupportedOperationException("MySQL CDC startup mode is not implemented: " + asModeString());
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StartupOptions that && startupMode == that.startupMode;
    }

    @Override
    public int hashCode() {
        return startupMode.hashCode();
    }

    @Override
    public String toString() {
        return "StartupOptions{" + asModeString() + "}";
    }
}
