package io.yak.ops.common.enums.modeling;

/**
 * Lifecycle of one physical publication attempt.
 *
 * <p>{@link #UNKNOWN} is a first-class outcome, not an error encoding: the DDL was submitted but the
 * engine result could not be determined. Such a record is reconciled later by re-reading the target,
 * and is never retried automatically, because a blind retry could create twice.
 */
public enum PublicationStatus {
    /** Recorded before the engine is contacted. */
    PENDING,
    /** The table exists and matches the model contract. */
    SUCCEEDED,
    /** The engine rejected the DDL, or an existing table does not match the model contract. */
    FAILED,
    /** The DDL result could not be determined; requires reconciliation. */
    UNKNOWN;

    /**
     * Resolves a stored value without guessing.
     *
     * @param value persisted status text
     * @return matching status, or null when the row carries an unknown value
     */
    public static PublicationStatus from(String value) {
        if (value == null) return null;
        for (var status : values()) {
            if (status.name().equals(value)) return status;
        }
        return null;
    }

    /** Whether the record reached a state that reconciliation must not change. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
