package io.yak.ops.business.task.submission;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;

/**
 * Prepared inputs for one Task admission transaction.
 *
 * <p>The executable plugin is a live capability used for read-only plan revalidation
 * during admission. This request holds that capability without invoking or owning its
 * lifecycle and therefore has identity semantics rather than value equality.
 */
public final class AdmissionRequest {

    private final TaskExecutionContext context;
    private final String requestId;
    private final String requestFingerprint;
    private final String operatorId;
    private final TaskParameters parameters;
    private final TaskExecutionPlan plan;
    private final ExecutableTaskPlugin plugin;

    /**
     * Creates the inputs of a validated Task submission.
     *
     * @param context trusted execution identity and pinned Definition version
     * @param requestId caller-stable idempotency key
     * @param requestFingerprint non-secret submission intent fingerprint
     * @param operatorId actor initiating execution
     * @param parameters validated immutable Task parameters
     * @param plan resolved execution resources and binding revision
     * @param plugin executable Task type used to prepare and revalidate the plan
     */
    public AdmissionRequest(
            TaskExecutionContext context,
            String requestId,
            String requestFingerprint,
            String operatorId,
            TaskParameters parameters,
            TaskExecutionPlan plan,
            ExecutableTaskPlugin plugin) {
        this.context = context;
        this.requestId = requestId;
        this.requestFingerprint = requestFingerprint;
        this.operatorId = operatorId;
        this.parameters = parameters;
        this.plan = plan;
        this.plugin = plugin;
    }

    /** Returns the pinned execution identity. */
    public TaskExecutionContext context() {
        return context;
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
    public TaskExecutionPlan plan() {
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
