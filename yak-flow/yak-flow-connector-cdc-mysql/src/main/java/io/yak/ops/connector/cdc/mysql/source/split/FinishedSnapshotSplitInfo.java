package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.base.source.meta.split.FinishedSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import java.util.Objects;

/**
 * One normalized snapshot chunk's immutable Binlog filtering fence.
 *
 * <p>The High watermark is recorded only after bounded Backfill and normalized snapshot
 * emission. It must survive Checkpoint and be included in the final Binlog split so events
 * already reflected in an initial chunk are not replayed into the JDBC Sink.
 *
 * @param tableId original MySQL source table
 * @param splitId stable source split identifier
 * @param lowerInclusive nullable lower key boundary
 * @param upperExclusive nullable upper key boundary
 * @param highWatermark event boundary included in the normalized snapshot
 */
public final class FinishedSnapshotSplitInfo implements FinishedSnapshotSplit<DebeziumOffset> {
    private final TableId tableId;
    private final String splitId;
    private final Long lowerInclusive;
    private final Long upperExclusive;
    private final DebeziumOffset highWatermark;
    private final ChunkRange chunkRange;

    public FinishedSnapshotSplitInfo(
            TableId tableId, String splitId, Long lowerInclusive, Long upperExclusive, DebeziumOffset highWatermark) {
        this(tableId, splitId, lowerInclusive, upperExclusive, highWatermark, null);
    }

    public FinishedSnapshotSplitInfo(
            TableId tableId,
            String splitId,
            Long lowerInclusive,
            Long upperExclusive,
            DebeziumOffset highWatermark,
            ChunkRange chunkRange) {
        Objects.requireNonNull(tableId, "tableId");
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Finished chunk must have a stable split ID");
        }
        Objects.requireNonNull(highWatermark, "highWatermark");
        if (chunkRange != null
                && (!tableId.equals(chunkRange.tableId()) || lowerInclusive != null || upperExclusive != null)) {
            throw new IllegalArgumentException("Typed finished chunk cannot reuse BIGINT boundaries");
        }
        if (lowerInclusive != null && upperExclusive != null && lowerInclusive >= upperExclusive) {
            throw new IllegalArgumentException("Finished chunk boundaries are not increasing");
        }

        this.tableId = tableId;
        this.splitId = splitId;
        this.lowerInclusive = lowerInclusive;
        this.upperExclusive = upperExclusive;
        this.highWatermark = highWatermark;
        this.chunkRange = chunkRange;
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public Long lowerInclusive() {
        return lowerInclusive;
    }

    public Long upperExclusive() {
        return upperExclusive;
    }

    @Override
    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    public ChunkRange chunkRange() {
        return chunkRange;
    }

    // Flink CDC getter names and non-record value semantics.
    public TableId getTableId() {
        return tableId;
    }

    public String getSplitId() {
        return splitId;
    }

    public Object[] getSplitStart() {
        Object start = chunkRange != null ? chunkRange.start() : lowerInclusive;
        return start == null ? null : new Object[] {start};
    }

    public Object[] getSplitEnd() {
        Object end = chunkRange != null ? chunkRange.end() : upperExclusive;
        return end == null ? null : new Object[] {end};
    }

    public DebeziumOffset getHighWatermark() {
        return highWatermark;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinishedSnapshotSplitInfo that)) {
            return false;
        }
        return Objects.equals(tableId, that.tableId)
                && Objects.equals(splitId, that.splitId)
                && Objects.equals(lowerInclusive, that.lowerInclusive)
                && Objects.equals(upperExclusive, that.upperExclusive)
                && Objects.equals(highWatermark, that.highWatermark)
                && Objects.equals(chunkRange, that.chunkRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, splitId, lowerInclusive, upperExclusive, highWatermark, chunkRange);
    }

    /** Returns whether a BIGINT key belongs to this chunk's half-open range. */
    public boolean contains(long key) {
        if (chunkRange != null) throw new IllegalStateException("Typed chunk is not a BIGINT range");
        return (lowerInclusive == null || key >= lowerInclusive) && (upperExclusive == null || key < upperExclusive);
    }
}
