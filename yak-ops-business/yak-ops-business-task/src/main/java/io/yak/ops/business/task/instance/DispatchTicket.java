package io.yak.ops.business.task.instance;

import java.util.Objects;

/**
 * Durable one-time dispatch ownership and pinned Task Definition identity.
 *
 * <p>The dispatch token is an opaque execution credential and must not appear in diagnostics.
 */
public final class DispatchTicket {

    private final String workspaceId;
    private final String instanceId;
    private final String attemptId;
    private final String definitionId;
    private final int definitionVersion;
    private final String taskType;
    private final String resourceRevision;
    private final String dispatchToken;

    /**
     * Creates one durable dispatch claim.
     *
     * @param workspaceId owning Workspace
     * @param instanceId logical Task Instance
     * @param attemptId physical execution Attempt
     * @param definitionId pinned Task Definition
     * @param definitionVersion pinned Task Definition version
     * @param taskType executable Task plugin type
     * @param resourceRevision admitted resource binding revision
     * @param dispatchToken opaque one-time dispatch credential
     */
    public DispatchTicket(
            String workspaceId,
            String instanceId,
            String attemptId,
            String definitionId,
            int definitionVersion,
            String taskType,
            String resourceRevision,
            String dispatchToken) {
        this.workspaceId = workspaceId;
        this.instanceId = instanceId;
        this.attemptId = attemptId;
        this.definitionId = definitionId;
        this.definitionVersion = definitionVersion;
        this.taskType = taskType;
        this.resourceRevision = resourceRevision;
        this.dispatchToken = dispatchToken;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String instanceId() {
        return instanceId;
    }

    public String attemptId() {
        return attemptId;
    }

    public String definitionId() {
        return definitionId;
    }

    public int definitionVersion() {
        return definitionVersion;
    }

    public String taskType() {
        return taskType;
    }

    public String resourceRevision() {
        return resourceRevision;
    }

    public String dispatchToken() {
        return dispatchToken;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DispatchTicket that)) {
            return false;
        }
        return definitionVersion == that.definitionVersion
                && Objects.equals(workspaceId, that.workspaceId)
                && Objects.equals(instanceId, that.instanceId)
                && Objects.equals(attemptId, that.attemptId)
                && Objects.equals(definitionId, that.definitionId)
                && Objects.equals(taskType, that.taskType)
                && Objects.equals(resourceRevision, that.resourceRevision)
                && Objects.equals(dispatchToken, that.dispatchToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                workspaceId,
                instanceId,
                attemptId,
                definitionId,
                definitionVersion,
                taskType,
                resourceRevision,
                dispatchToken);
    }

    @Override
    public String toString() {
        return "DispatchTicket{instanceId=" + instanceId + ", attemptId=" + attemptId + ", dispatchToken=redacted}";
    }
}
