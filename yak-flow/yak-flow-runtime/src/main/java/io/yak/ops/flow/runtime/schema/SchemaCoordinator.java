package io.yak.ops.flow.runtime.schema;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import io.yak.ops.flow.runtime.tasks.OneInputStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serializes runtime schema transitions across Source readers and Sink Writers.
 *
 * <p>A transition validates all Writers, pauses every Source, drains already-emitted records in
 * pipeline order, flushes all Writers, applies the planned target events, updates Writer plans,
 * and only then commits the schema version and resumes the Sources. The checkpoint coordinator
 * uses the same event loop to fence transitions and persist a matching schema version.
 *
 * <p>This is local-runtime coordination. It does not claim distributed exactly-once semantics.
 */
public final class SchemaCoordinator implements AutoCloseable {

    private static final long CLOSE_TIMEOUT_SECONDS = 45;

    private final SchemaManager manager;
    private final MetadataApplier metadataApplier;
    private final List<SourceOperatorStreamTask<?, ?>> sourceTasks;
    private final List<OneInputStreamTask> pipelineTasks;
    private final List<SchemaChangeParticipant> sinkParticipants;
    private final Consumer<Throwable> onFailure;
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-schema-coordinator-", 0).factory());
    private final Object lock = new Object();
    private final List<PendingChange> deferred = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable fatalFailure;
    private boolean checkpointPauseRequested;
    private boolean checkpointPaused;

    public SchemaCoordinator(
            SchemaManager manager,
            MetadataApplier metadataApplier,
            List<? extends SourceOperatorStreamTask<?, ?>> sourceTasks,
            List<OneInputStreamTask> pipelineTasks,
            List<? extends SchemaChangeParticipant> sinkParticipants,
            Consumer<Throwable> onFailure) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.metadataApplier = Objects.requireNonNull(metadataApplier, "metadataApplier");
        this.sourceTasks = List.copyOf(Objects.requireNonNull(sourceTasks, "sourceTasks"));
        this.pipelineTasks = List.copyOf(Objects.requireNonNull(pipelineTasks, "pipelineTasks"));
        this.sinkParticipants = List.copyOf(Objects.requireNonNull(sinkParticipants, "sinkParticipants"));
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        if (this.sourceTasks.isEmpty() || this.pipelineTasks.isEmpty() || this.sinkParticipants.isEmpty()) {
            throw new IllegalArgumentException("Schema coordination requires Source, input and Sink tasks");
        }
    }

    /**
     * Applies one replay-stable request. The Source must not emit records after the event until
     * the returned stage completes; Source implementations must not block their mailbox thread.
     */
    public CompletionStage<Long> applySchemaChange(SchemaChangeRequest request, int sourceSubtask) {
        Objects.requireNonNull(request, "request");
        if (sourceSubtask < 0 || sourceSubtask >= sourceTasks.size()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Source subtask"));
        }
        CompletableFuture<Long> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            if (fatalFailure != null) {
                return CompletableFuture.failedFuture(fatalFailure);
            }
            PendingChange pending = new PendingChange(request, sourceSubtask, result);
            if (checkpointPauseRequested || checkpointPaused) {
                deferred.add(pending);
            } else {
                submit(() -> process(pending));
            }
        }
        return result;
    }

    /** Fences new schema requests and waits for the active transition to finish. */
    public CompletableFuture<Void> pauseForCheckpoint() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            if (checkpointPauseRequested || checkpointPaused) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is already paused"));
            }
            checkpointPauseRequested = true;
            submit(() -> {
                synchronized (lock) {
                    checkpointPaused = true;
                    checkpointPauseRequested = false;
                }
                result.complete(null);
            }, result);
        }
        return result;
    }

    /** Captures only committed versions; caller must first await pauseForCheckpoint(). */
    public CheckpointSnapshot.SerializedState snapshotState() throws IOException {
        synchronized (lock) {
            if (!checkpointPaused || checkpointPauseRequested) {
                throw new IllegalStateException("SchemaCoordinator must be paused before checkpoint snapshot");
            }
        }
        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        return new CheckpointSnapshot.SerializedState(serializer.getVersion(), serializer.serialize(manager.snapshot()));
    }

    /** Restores committed source/target schemas before any task or SourceReader is started. */
    public static SchemaManager restoreManager(CheckpointSnapshot.SerializedState state) throws IOException {
        SchemaManager manager = new SchemaManager();
        if (state != null) {
            manager.restore(new SchemaManagerSnapshotSerializer().deserialize(state.version(), state.bytes()));
        }
        return manager;
    }

    /** Releases the checkpoint fence and schedules requests that arrived while it was held. */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SchemaCoordinator is closed"));
            }
            submit(() -> {
                List<PendingChange> pendingChanges;
                synchronized (lock) {
                    checkpointPaused = false;
                    checkpointPauseRequested = false;
                    pendingChanges = List.copyOf(deferred);
                    deferred.clear();
                }
                result.complete(null);
                for (PendingChange pending : pendingChanges) {
                    submit(() -> process(pending), pending.result());
                }
            }, result);
        }
        return result;
    }

    public long currentVersion(io.yak.ops.core.data.TableId targetTable) {
        return manager.currentVersion(targetTable);
    }

    private void process(PendingChange pending) {
        SchemaChangeRequest request = pending.request();
        boolean begun = false;
        boolean ddlStarted = false;
        try {
            ensureHealthy();
            SchemaManager.Change change = manager.begin(request);
            if (change.duplicate()) {
                pending.result().complete(change.version());
                return;
            }
            begun = true;

            // Validate all immutable Writer replacements before pausing input or mutating the target.
            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.validateSchemaChange(request));
            }

            List<SourceOperatorStreamTask<?, ?>> paused = new ArrayList<>();
            for (SourceOperatorStreamTask<?, ?> source : sourceTasks) {
                await(source.pauseForSchemaChange());
                paused.add(source);
            }

            // Drain every upstream stage before its downstream stage. This preserves channel order
            // without changing the connector's source/binlog implementation.
            for (OneInputStreamTask task : pipelineTasks) {
                await(task.drainForSchemaChange());
            }
            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.prepareSchemaChange(request));
            }

            Set<SchemaChangeEventType> accepted = new HashSet<>();
            for (SchemaChangeEvent event : request.targetChanges()) {
                accepted.add(event.getType());
            }
            metadataApplier.setAcceptedSchemaEvolutionTypes(Set.copyOf(accepted));
            ddlStarted = true;
            for (SchemaChangeEvent event : request.targetChanges()) {
                ensureHealthy();
                metadataApplier.applySchemaChange(event);
            }

            for (SchemaChangeParticipant participant : sinkParticipants) {
                await(participant.commitSchemaChange(request, change.version()));
            }
            long committedVersion = manager.complete(request);
            if (committedVersion != change.version()) {
                throw new IllegalStateException("Committed schema version changed during coordination");
            }
            for (SourceOperatorStreamTask<?, ?> source : paused) {
                await(source.resumeAfterSchemaChange());
            }
            pending.result().complete(committedVersion);
        } catch (Throwable error) {
            Throwable failure = unwrap(error);
            if (begun && !ddlStarted) {
                manager.abort(request);
            }
            fail(failure);
            pending.result().completeExceptionally(failure);
        }
    }

    private void fail(Throwable failure) {
        if (fatalFailure == null) {
            synchronized (lock) {
                if (fatalFailure == null) {
                    fatalFailure = failure;
                }
            }
            try {
                onFailure.accept(failure);
            } catch (Throwable callbackFailure) {
                if (callbackFailure != failure) failure.addSuppressed(callbackFailure);
            }
        }
    }

    private void ensureHealthy() {
        if (closed.get()) throw new IllegalStateException("SchemaCoordinator is closed");
        Throwable failure = fatalFailure;
        if (failure != null) throw new IllegalStateException("SchemaCoordinator has failed", failure);
    }

    private void submit(Runnable action) {
        submit(action, null);
    }

    private void submit(Runnable action, CompletableFuture<?> failureTarget) {
        try {
            eventLoop.execute(() -> {
                try {
                    ensureHealthy();
                    action.run();
                } catch (Throwable failure) {
                    if (failureTarget != null) failureTarget.completeExceptionally(failure);
                    else fail(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            if (failureTarget != null) failureTarget.completeExceptionally(failure);
            else fail(failure);
        }
    }

    private static void await(CompletionStage<?> stage) throws Exception {
        try {
            stage.toCompletableFuture().get();
        } catch (ExecutionException failure) {
            Throwable cause = unwrap(failure);
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof ExecutionException
                        || result instanceof java.util.concurrent.CompletionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        List<PendingChange> waiting;
        synchronized (lock) {
            waiting = List.copyOf(deferred);
            deferred.clear();
        }
        IllegalStateException closing = new IllegalStateException("SchemaCoordinator closed before schema change completed");
        waiting.forEach(pending -> pending.result().completeExceptionally(closing));
        eventLoop.shutdown();
        if (!eventLoop.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            try {
                metadataApplier.cancel();
            } catch (Exception cancelFailure) {
                closing.addSuppressed(cancelFailure);
            }
            eventLoop.shutdownNow();
            if (!eventLoop.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("SchemaCoordinator could not stop active schema work", closing);
            }
        }
        metadataApplier.close();
    }

    private record PendingChange(SchemaChangeRequest request, int sourceSubtask, CompletableFuture<Long> result) {
        private PendingChange {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }
}
