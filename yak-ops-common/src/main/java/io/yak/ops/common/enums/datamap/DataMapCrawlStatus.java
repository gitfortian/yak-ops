package io.yak.ops.common.enums.datamap;

/** Lifecycle of one data map crawl run. */
public enum DataMapCrawlStatus {
    /** Submitted and waiting for a worker. */
    PENDING,
    /** Claimed by a worker. */
    RUNNING,
    /** Enumeration completed; per-table failures may still exist. */
    SUCCEEDED,
    /** Enumeration, budget, ceiling or restart stopped the run. */
    FAILED;

    /** Returns true when the run will not change again without a new submission. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    /** Returns true while the run holds its datasource scope lock. */
    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
