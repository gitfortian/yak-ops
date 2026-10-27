package io.yak.ops.flow.runtime.checkpoint;

import java.io.IOException;
import java.util.Optional;

/**
 * Owns the durable, completed checkpoints of one job.
 *
 * <p>Like Flink's CompletedCheckpointStore, this contract exposes only published
 * checkpoints for recovery. In-flight task acknowledgements belong to
 * {@link PendingCheckpoint}; the storage implementation must never surface
 * a partially published checkpoint.
 */
public interface CompletedCheckpointStore extends AutoCloseable {

    /** Returns only the latest durably published checkpoint for the expected JobGraph. */
    Optional<CheckpointSnapshot> loadLatest(String expectedGraphSignature) throws IOException;

    /** Publishes a completely acknowledged checkpoint atomically and durably. */
    void save(CheckpointSnapshot checkpoint) throws IOException;

    @Override
    void close() throws IOException;
}
