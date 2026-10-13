package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.task.TaskTriggerType;

/**
 * Trusted request to execute a published Task Definition.
 *
 * <p>HTTP callers must obtain the Workspace and operator from authentication.
 * Scheduler and Workflow callers must use their authorized internal identities.
 *
 * @param workspaceId authorized Workspace
 * @param definitionId Task Definition to execute
 * @param triggerType namespace of the submission event
 * @param requestId stable idempotency identity within the trigger namespace
 * @param operatorId actor or system principal requesting execution
 */
public record TaskSubmissionCommand(
        String workspaceId, String definitionId, TaskTriggerType triggerType, String requestId, String operatorId) {}
