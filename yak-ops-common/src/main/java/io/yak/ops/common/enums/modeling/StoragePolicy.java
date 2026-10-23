package io.yak.ops.common.enums.modeling;

/** Storage strategies a logical model can declare. */
public enum StoragePolicy {
    /** Append one day of data per run. */
    DAILY_INCREMENTAL,
    /** Replace the partition with a full day of data per run. */
    DAILY_FULL
}
