package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.plan.TablePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Owns one Task's ordered target preparation and driver-level cancellation requests. */
public final class SchemaPreparation {
    private final PipelineBuilder builder;
    private final JobPlanner planner;
    private boolean canceled;
    private MetadataApplier active;

    public SchemaPreparation(PipelineBuilder builder, JobPlanner planner) {
        this.builder = java.util.Objects.requireNonNull(builder, "builder");
        this.planner = java.util.Objects.requireNonNull(planner, "planner");
    }

    /**
     * Creates or verifies all admitted targets before any data pipeline is submitted.
     *
     * @param plan complete admitted table set
     * @param identity trusted Task identity
     * @param progress Task-owned table observations
     * @return the same logical plan with verified physical targets
     * @throws Exception if any operation, readback, or resource close fails
     */
    public JobPlan prepare(JobPlan plan, TaskExecutionIdentity identity, TableExecutionProgress progress)
            throws Exception {
        List<TablePlan> prepared = new ArrayList<>();
        for (int index = 0; index < plan.tables().size(); index++) {
            TablePlan table = plan.tables().get(index);
            checkCanceled();
            progress.schemaStarted(index);
            try {
                TablePlan physical;
                try (MetadataApplier applier = builder.createMetadataApplier(table, identity)) {
                    synchronized (this) {
                        checkCanceled();
                        active = applier;
                    }
                    applier.applySchemaChange(table.schemaChange());
                    checkCanceled();
                    physical = planner.preparedTarget(table, identity);
                    checkCanceled();
                } finally {
                    synchronized (this) {
                        active = null;
                    }
                }
                prepared.add(physical);
                progress.schemaFinished(index, table.targetSchema() == null ? "PREPARED" : "VERIFIED");
            } catch (Exception failure) {
                if (failure instanceof SchemaEvolveException schema && schema.isOutcomeUnknown()) {
                    progress.schemaUnknown(index);
                } else if (isCanceled()) progress.schemaCanceled(index);
                else progress.schemaFailed(index, "SCHEMA_PREPARATION_FAILED");
                throw failure;
            }
        }
        return new JobPlan(prepared, plan.admissionPlan());
    }

    /** Records cancellation synchronously before an asynchronous driver request is dispatched. */
    public synchronized void requestCancellation() {
        canceled = true;
    }

    /**
     * Records cancellation and requests driver cancellation without releasing target ownership.
     *
     * @throws Exception if the active driver rejects the request
     */
    public void cancel() throws Exception {
        MetadataApplier applying;
        synchronized (this) {
            canceled = true;
            applying = active;
        }
        if (applying != null) applying.cancel();
    }

    private synchronized boolean isCanceled() {
        return canceled || Thread.currentThread().isInterrupted();
    }

    private void checkCanceled() {
        if (isCanceled()) throw new CancellationException("Schema preparation was canceled");
    }
}
