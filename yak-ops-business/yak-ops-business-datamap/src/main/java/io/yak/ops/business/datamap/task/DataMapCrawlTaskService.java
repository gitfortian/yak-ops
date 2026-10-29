package io.yak.ops.business.datamap.task;

import io.yak.ops.common.bean.dto.datamap.DataMapCrawlTaskSaveDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlTaskVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import io.yak.ops.common.page.PagingData;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages metadata collection task configuration and dispatch.
 *
 * <p>A task owns configuration and lifecycle; its runs remain execution state. A run snapshots the
 * exclusions at submission, so editing a task never changes a run that already started.
 */
public interface DataMapCrawlTaskService {

    /**
     * Lists tasks of the Workspace with the newest run summary of each.
     *
     * @param workspaceId current Workspace
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return one page of tasks, newest first
     */
    PagingData<DataMapCrawlTaskVO> list(String workspaceId, int pageNo, int pageSize);

    /**
     * Reads one task with its newest run summary.
     *
     * @param workspaceId current Workspace
     * @param taskId task identity
     * @return the task and its latest run status, or null run fields when it never ran
     */
    DataMapCrawlTaskVO detail(String workspaceId, String taskId);

    /**
     * Creates a task and optionally schedules it.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated creator
     * @param input configuration to store; status must be DRAFT or RUNNING
     * @return the stored task
     */
    DataMapCrawlTaskVO create(String workspaceId, String actor, DataMapCrawlTaskSaveDTO input);

    /**
     * Replaces the configuration of a task.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated editor
     * @param taskId task identity
     * @param input configuration to store; saving as RUNNING schedules a CRON task from now
     * @return the stored task
     */
    DataMapCrawlTaskVO update(String workspaceId, String actor, String taskId, DataMapCrawlTaskSaveDTO input);

    /**
     * Takes a task online or offline. A CRON task is scheduled from the transition time.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated operator
     * @param taskId task identity
     * @param target RUNNING to take online, OFFLINE to pause
     * @return the task after the transition
     */
    DataMapCrawlTaskVO changeStatus(String workspaceId, String actor, String taskId, DataMapCrawlTaskStatus target);

    /**
     * Deletes a draft or offline task together with its run history.
     *
     * @param workspaceId current Workspace
     * @param taskId task identity
     */
    void delete(String workspaceId, String taskId);

    /**
     * Starts one manual run of an online task.
     *
     * <p>A task that already holds an active run is refused by the run's scope lock, so duplicate
     * manual execution cannot overlap.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated operator
     * @param taskId task identity
     * @return the pending run
     */
    DataMapCrawlVO run(String workspaceId, String actor, String taskId);

    /**
     * Starts a manual run that re-enumerates the tables an earlier run of the task failed on.
     *
     * <p>The new run snapshots the current task exclusions and keeps the task as its owner, so
     * troubleshooting never detaches the retry from the schedule configuration. A paused task may
     * be retried; the active-scope lock still prevents overlapping runs.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated operator
     * @param taskId task that owns the failed run
     * @param crawlId failed run to retry
     * @return the pending retry run
     */
    DataMapCrawlVO retry(String workspaceId, String actor, String taskId, String crawlId);

    /**
     * Computes the next occurrences of a Cron expression in the scheduler's time zone.
     *
     * <p>The preview reuses the same parser and clock zone as dispatch, so the shown times cannot
     * drift from what the poller would actually submit.
     *
     * @param cronExpression Spring six-field Cron expression
     * @return up to three future occurrences, earliest first
     */
    List<LocalDateTime> previewSchedule(String cronExpression);

    /**
     * Starts every due CRON task.
     *
     * <p>Each occurrence is advanced conditionally before submission, so two pollers cannot start
     * the same occurrence twice. A busy datasource skips only this occurrence.
     *
     * @return number of runs submitted
     */
    int dispatchDue();
}
