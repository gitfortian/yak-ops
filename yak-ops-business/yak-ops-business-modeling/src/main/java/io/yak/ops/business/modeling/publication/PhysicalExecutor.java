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
     * One executed statement and how long the engine took on it.
     *
     * @param statement the SQL text
     * @param durationMs engine execution time in milliseconds
     */
    record StatementResult(String statement, long durationMs) {}

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
     * @return results of the statements that completed, in order; empty when the first one failed
     * @throws PhysicalExecutionException when the engine refuses a statement or the outcome is
     *     undetermined; the exception carries the results completed before the failure
     */
    List<StatementResult> execute(String workspaceId, String datasourceId, List<String> statements);
}
