package io.yak.ops.business.task.submission;

/** Admits Task submissions independently of the physical execution engine. */
public interface SubmissionService {

    /**
     * Admits a trusted execution request or returns its original idempotent submission.
     *
     * @param command authorized Definition, Workspace and trigger identity
     * @return accepted or previously admitted Instance and Attempt identity
     */
    SubmissionResult submit(TaskSubmissionCommand command);
}
