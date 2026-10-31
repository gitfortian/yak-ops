package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/**
 * Failure backlog of the tasks that have run at least once.
 *
 * <p>Both figures read the newest run of each task only: a failure that a later run already fixed
 * is not pending anymore, so counting every historical run would inflate the backlog forever.
 */
@Getter
@Setter
public class DataMapTaskFailureRow {
    /** Sum of failed tables over the newest run of each task. */
    private Long failedTables;
    /** Tasks whose newest run is failed or left failed tables. */
    private Long abnormalTasks;
}
