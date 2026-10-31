package io.yak.ops.common.bean.vo.datamap;

import lombok.Getter;
import lombok.Setter;

/**
 * Workspace-level collection health, read once per task page load.
 *
 * <p>Everything here is counted across the whole Workspace rather than the visible page: a health
 * figure that only describes the current page would disagree with the table right below it. Run
 * counts are limited to a rolling window, named by {@code windowHours}.
 */
@Getter
@Setter
public class DataMapCrawlSummaryVO {

    /** Task configurations in the Workspace, every status. */
    private long taskTotal;

    /** Tasks that are online, so the scheduler owns them. */
    private long onlineTaskCount;

    /** Length of the run window the counts below describe. */
    private int windowHours;

    /** Runs submitted inside the window, every outcome. */
    private long runTotal;

    /** Runs that ended with every table catalogued and no failure. */
    private long runSucceeded;

    /** Runs that ended succeeded but left failed tables; derived, never stored as a status. */
    private long runPartial;

    /** Runs that aborted before cataloguing anything. */
    private long runFailed;

    /** Runs cut off by an application restart. */
    private long runInterrupted;

    /** Runs still being executed. */
    private long runRunning;

    /** Runs queued and not yet claimed. */
    private long runPending;

    /**
     * Failed tables still waiting for a retry: the failed count summed over the newest run of each
     * task. An older run that failed but was superseded is deliberately excluded, because only the
     * newest attempt describes the current state of the namespace.
     */
    private long failedTableCount;

    /** Tasks whose newest run is failed or partially succeeded. */
    private long abnormalTaskCount;

    /** Terminal runs submitted today, every outcome. */
    private long todayRunTotal;

    /** Terminal runs submitted today that ended succeeded. */
    private long todayRunSucceeded;

    /** Terminal runs submitted in the last seven days, every outcome. */
    private long weekRunTotal;

    /** Terminal runs submitted in the last seven days that ended succeeded. */
    private long weekRunSucceeded;

    /** Average duration of the successful runs in the last seven days, in seconds. */
    private long avgDurationSeconds;

    /** P95 duration of the successful runs in the last seven days, in seconds. */
    private long p95DurationSeconds;

    /** Columns of active assets, the denominator of {@code describedColumnCount}. */
    private long columnTotal;

    /** Columns carrying a comment read from the source database. */
    private long describedColumnCount;
}
