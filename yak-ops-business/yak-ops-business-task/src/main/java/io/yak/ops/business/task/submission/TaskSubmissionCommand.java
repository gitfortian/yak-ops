package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.task.TaskTriggerType;
import java.time.Instant;

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
 * @param scheduleId originating schedule or null for non-scheduled submissions
 * @param scheduleRevision originating trigger revision or null
 * @param scheduledTime logical UTC occurrence or null
 */
public record TaskSubmissionCommand(
        String workspaceId,
        String definitionId,
        TaskTriggerType triggerType,
        String requestId,
        String operatorId,
        String scheduleId,
        Long scheduleRevision,
        Instant scheduledTime) {}
