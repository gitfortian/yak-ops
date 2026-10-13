package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;

/**
 * Prepared, read-only inputs of one Task admission transaction.
 *
 * @param identity trusted execution identity and pinned Definition version
 * @param triggerType namespace of the idempotent submission
 * @param requestId caller-stable idempotency key
 * @param requestFingerprint non-secret submission intent fingerprint
 * @param operatorId actor initiating execution
 * @param parameters validated immutable Task parameters
 * @param plan resolved execution resources and binding revision
 * @param plugin executable Task type used to prepare the plan
 */
public record AdmissionRequest(
        TaskExecutionIdentity identity,
        TaskTriggerType triggerType,
        String requestId,
        String requestFingerprint,
        String operatorId,
        TaskParameters parameters,
        TaskAdmissionPlan plan,
        ExecutableTaskPlugin plugin) {}
