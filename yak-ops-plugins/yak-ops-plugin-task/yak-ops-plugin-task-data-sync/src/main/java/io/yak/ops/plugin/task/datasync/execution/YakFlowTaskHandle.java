package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.core.execution.JobClient;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Adapts the YakFlow JobClient lifecycle to the Task platform's TaskHandle contract. */
final class YakFlowTaskHandle implements TaskHandle {

    private final JobClient client;

    YakFlowTaskHandle(JobClient client) {
        this.client = client;
    }

    @Override
    public Optional<TaskRuntimeReference> runtimeReference() {
        return Optional.of(new TaskRuntimeReference("YAKFLOW", client.getJobID().toString()));
    }

    @Override
    public CompletionStage<TaskOutcome> completion() {
        return client.getJobExecutionResult()
                .handle((result, failure) -> failure)
                .thenCompose(resultFailure -> client.getJobStatus().handle((status, statusFailure) -> {
                    if (statusFailure != null) {
                        if (resultFailure != null && statusFailure != resultFailure) {
                            statusFailure.addSuppressed(resultFailure);
                        }
                        throw new CompletionException(statusFailure);
                    }
                    return switch (status) {
                        case FINISHED -> TaskOutcome.FINISHED;
                        case CANCELED -> TaskOutcome.CANCELED;
                        case FAILED -> TaskOutcome.FAILED;
                        default ->
                                throw new IllegalStateException("YakFlow returned a result before terminal Job state");
                    };
                }));
    }

    @Override
    public CompletionStage<Void> cancel() {
        return client.cancel();
    }
}
