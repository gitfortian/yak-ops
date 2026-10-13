package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;

/**
 * Prepared inputs for one Task admission transaction.
 *
 * <p>The executable plugin is a live capability used for read-only plan revalidation
 * during admission. This request holds that capability without invoking or owning its
 * lifecycle and therefore has identity semantics rather than value equality.
 */
public final class AdmissionRequest {

    private final TaskExecutionIdentity identity;
    private final TaskTriggerType triggerType;
    private final String requestId;
    private final String requestFingerprint;
    private final String operatorId;
    private final TaskParameters parameters;
    private final TaskAdmissionPlan plan;
    private final ExecutableTaskPlugin plugin;

    /**
     * Creates the inputs of a validated Task submission.
     *
     * @param identity trusted execution identity and pinned Definition version
     * @param triggerType namespace of the idempotent submission
     * @param requestId caller-stable idempotency key
     * @param requestFingerprint non-secret submission intent fingerprint
     * @param operatorId actor initiating execution
     * @param parameters validated immutable Task parameters
     * @param plan resolved execution resources and binding revision
     * @param plugin executable Task type used to prepare and revalidate the plan
     */
    public AdmissionRequest(
            TaskExecutionIdentity identity,
            TaskTriggerType triggerType,
            String requestId,
            String requestFingerprint,
            String operatorId,
            TaskParameters parameters,
            TaskAdmissionPlan plan,
            ExecutableTaskPlugin plugin) {
        this.identity = identity;
        this.triggerType = triggerType;
        this.requestId = requestId;
        this.requestFingerprint = requestFingerprint;
        this.operatorId = operatorId;
        this.parameters = parameters;
        this.plan = plan;
        this.plugin = plugin;
    }

    /** Returns the pinned execution identity. */
    public TaskExecutionIdentity identity() {
        return identity;
    }

    /** Returns the idempotent submission trigger. */
    public TaskTriggerType triggerType() {
        return triggerType;
    }

    /** Returns the idempotency key associated with the submission. */
    public String requestId() {
        return requestId;
    }

    /** Returns the submission intent fingerprint. */
    public String requestFingerprint() {
        return requestFingerprint;
    }

    /** Returns the actor initiating this submission. */
    public String operatorId() {
        return operatorId;
    }

    /** Returns the validated Task parameters. */
    public TaskParameters parameters() {
        return parameters;
    }

    /** Returns the admitted resource plan. */
    public TaskAdmissionPlan plan() {
        return plan;
    }

    /** Returns the executable plugin used for plan revalidation. */
    public ExecutableTaskPlugin plugin() {
        return plugin;
    }

    /** Omits plugin, parameters, and submission identifiers from diagnostics. */
    @Override
    public String toString() {
        return "AdmissionRequest{contents=redacted}";
    }
}
