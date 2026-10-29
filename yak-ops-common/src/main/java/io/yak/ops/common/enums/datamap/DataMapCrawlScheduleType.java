package io.yak.ops.common.enums.datamap;

/** How a metadata collection task is triggered. */
public enum DataMapCrawlScheduleType {
    /** Runs only when an operator starts one. */
    MANUAL,
    /** Runs on a persisted Cron schedule while the task is online. */
    CRON
}
