package io.yak.ops.task.execution;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.common.enums.task.TaskInstanceStatus;

/** Coordinates trusted Task submissions and cancellation without an engine-specific dependency. */
public interface TaskExecutionService {

    /**
     * Admits and dispatches one trusted Task submission.
     *
     * @param command authorized Workspace, Definition and trigger identity
     * @return persisted Instance and Attempt identity, including idempotent replay status
     */
    SubmissionResult submit(TaskSubmissionCommand command);

    /**
     * Persists cancellation intent and requests physical cancellation when a Job is active.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance to cancel
     * @return authoritative persisted Instance status
     */
    TaskInstanceStatus cancel(String workspaceId, String instanceId);
}
