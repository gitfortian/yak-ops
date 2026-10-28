package io.yak.ops.common.enums.datamap;

/** Lifecycle of one data map crawl run. */
public enum DataMapCrawlStatus {
    /** Submitted and waiting for a worker. */
    PENDING,
    /** Claimed by a worker. */
    RUNNING,
    /** Enumeration completed; per-table failures may still exist. */
    SUCCEEDED,
    /** Enumeration, budget, ceiling or a run-level error stopped the run. */
    FAILED,
    /** Swept after an application restart; the scope lock was released in the same statement. */
    INTERRUPTED;

    /** Returns true when the run will not change again without a new submission. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == INTERRUPTED;
    }

    /** Returns true while the run holds its datasource scope lock. */
    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
