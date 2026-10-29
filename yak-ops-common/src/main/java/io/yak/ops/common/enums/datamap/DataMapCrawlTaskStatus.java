package io.yak.ops.common.enums.datamap;

/** Lifecycle of one metadata collection task, independent of its runs. */
public enum DataMapCrawlTaskStatus {
    /** Created but never scheduled. */
    DRAFT,
    /** Online: manual runs are allowed and a CRON task is scheduled. */
    RUNNING,
    /** Paused: configuration and history are kept, nothing is scheduled. */
    OFFLINE;

    /** Returns true when the task may hold a schedule. */
    public boolean isOnline() {
        return this == RUNNING;
    }
}
