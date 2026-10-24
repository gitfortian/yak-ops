package io.yak.ops.business.modeling.publication;

import java.util.List;

/**
 * Executes generated DDL against a datasource target.
 *
 * <p>A seam rather than a class call so publication can be tested without an engine, and so the
 * controlled DDL path can later be swapped without touching the publication rules.
 */
public interface PhysicalExecutor {

    /**
     * Executes the statements in order in auto-commit mode.
     *
     * <p>Statements are not rolled back together: the engine commits each one, exactly as the
     * proposal states, so a later failure leaves the earlier statements applied and the publication
     * is recorded accordingly.
     *
     * @param workspaceId authorized Workspace
     * @param datasourceId datasource to reach the target through
     * @param statements ordered statements produced by {@link PhysicalDdlGenerator}
     * @throws PhysicalExecutionException when the engine refuses a statement or the outcome is
     *     undetermined
     */
    void execute(String workspaceId, String datasourceId, List<String> statements);
}
