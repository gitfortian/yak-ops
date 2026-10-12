package io.yak.ops.common.enums.datasync;

/**
 * Selects the retry policy applied to a data synchronization execution.
 */
public enum DataSyncRetryPolicyMode {

    /** Determines retry eligibility using system rules and write-safety constraints. */
    SMART,

    /** Uses configured maximum attempts and fixed backoff between retries. */
    FIXED
}
