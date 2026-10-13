package io.yak.ops.business.task.submission;

import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;

/** Admits Task submissions independently of the physical execution engine. */
public interface SubmissionService {

    /**
     * Admits a trusted execution request or returns its original idempotent submission.
     *
     * @param command authorized Definition, Workspace and trigger identity
     * @return accepted or previously admitted Instance identity
     */
    SubmissionResult submit(TaskSubmissionCommand command);

    /**
     * Resolves a trusted fired occurrence using the same preparation and admission implementation.
     * @param trigger immutable registered occurrence; never accepted from an HTTP run request
     * @return durable admission, skip, rejection, or replay
     */
    ScheduleFireResult submitScheduled(ScheduleTrigger trigger);
}
