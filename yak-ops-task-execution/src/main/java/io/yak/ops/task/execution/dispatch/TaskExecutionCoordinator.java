package io.yak.ops.task.execution.dispatch;

import io.yak.ops.common.enums.task.TaskInstanceStatus;

/**
 * Coordinates dispatch and cancellation of durable Task Instances.
 *
 * <p>The implementation owns physical dispatch, while the Task platform owns
 * submission admission and persistent lifecycle transitions.
 */
public interface TaskExecutionCoordinator {

    /**
     * Dispatches one committed Task Instance.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId durable Task Instance identity
     */
    void dispatch(String workspaceId, String instanceId);

    /**
     * Records cancellation and forwards it to the active execution when available.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId durable Task Instance identity
     * @return current persistent Task Instance status
     */
    TaskInstanceStatus cancel(String workspaceId, String instanceId);
}
