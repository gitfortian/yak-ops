package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** Outcome distribution of the runs submitted inside one time window. */
@Getter
@Setter
public class DataMapRunSummaryRow {
    /** Runs in the window, every outcome. */
    private Long total;
    /** Runs that catalogued everything and failed on nothing. */
    private Long succeeded;
    /** Runs that ended succeeded but left failed tables. */
    private Long partial;
    /** Runs that aborted. */
    private Long failed;
    /** Runs cut off by a restart. */
    private Long interrupted;
    /** Runs still executing. */
    private Long running;
    /** Runs queued and not yet claimed. */
    private Long pending;
}
