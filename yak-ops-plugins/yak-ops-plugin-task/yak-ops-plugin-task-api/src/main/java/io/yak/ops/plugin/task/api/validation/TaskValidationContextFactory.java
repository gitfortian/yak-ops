package io.yak.ops.plugin.task.api.validation;

/**
 * Creates a read-only validation context bound to a trusted Workspace.
 *
 * <p>The Workspace identity is supplied by an authorized execution boundary,
 * not by plugin JSON or an asynchronous thread-local HTTP context.
 */
public interface TaskValidationContextFactory {

    /**
     * Creates a context for one authorized Workspace.
     *
     * @param workspaceId trusted Workspace identity
     * @return Workspace-bound read-only resource validation
     */
    TaskValidationContext forWorkspace(String workspaceId);
}
