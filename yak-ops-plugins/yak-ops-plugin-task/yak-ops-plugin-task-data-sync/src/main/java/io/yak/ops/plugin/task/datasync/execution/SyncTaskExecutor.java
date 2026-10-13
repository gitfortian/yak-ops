package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.pipeline.SyncCompiledPipeline;
import io.yak.ops.plugin.task.datasync.pipeline.SyncPipelineCompiler;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlan;
import io.yak.ops.plugin.task.datasync.plan.SyncJobPlanner;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Verifies admitted DATA_SYNC resource claims and submits the compiled Job to YakFlow.
 *
 * <p>Source/Sink selection, Connector creation and StreamGraph assembly belong to the compiler.
 * Task Instance/Attempt ownership and admission belong to the Task platform.
 */
public final class SyncTaskExecutor {

    private final SyncJobPlanner planner;
    private final SyncPipelineCompiler compiler;
    private final PipelineExecutor executor;

    public SyncTaskExecutor(SyncJobPlanner planner, SyncPipelineCompiler compiler, PipelineExecutor executor) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Starts one physical Job after rechecking the durable resource binding.
     *
     * @param parameters pinned Task parameters
     * @param plan admitted canonical resource claim
     * @param context trusted Task execution identity
     * @return asynchronous execution handle or pre-submission rejection
     */
    public CompletionStage<TaskHandle> start(
            TaskParameters parameters, TaskAdmissionPlan plan, TaskExecutionIdentity context) {
        boolean runtimeSubmissionInvoked = false;
        try {
            if (!(parameters instanceof SyncParameters pipeline)) {
                throw new IllegalArgumentException("Invalid DATA_SYNC parameter type");
            }
            SyncJobPlan prepared = planner.prepare(pipeline, context);
            if (!plan.equals(prepared.admissionPlan())) {
                throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
            }
            SyncCompiledPipeline compiled = compiler.compile(prepared, context);
            runtimeSubmissionInvoked = true;
            return executor.execute(compiled.graph(), compiled.configuration())
                    .thenApply(client -> (TaskHandle) new SyncJobHandle(client));
        } catch (RuntimeException error) {
            if (!runtimeSubmissionInvoked) {
                return CompletableFuture.failedFuture(
                        new TaskStartRejectedException("DATA_SYNC rejected before engine submission", error));
            }
            return CompletableFuture.failedFuture(error);
        }
    }
}
