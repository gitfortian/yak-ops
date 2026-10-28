/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Owns snapshot split discovery, assignment, completion and the checkpointed chunk cursor.
 *
 * <p>Follows Apache Flink CDC's MySqlSnapshotSplitAssigner (source pinned to
 * b806d26762862a76dc91ac81f16dbd7f6ec62821). The JDBC discovery thread remains in
 * YakFlow's SourceEnumerator; all state transitions are serialized on its coordinator thread.
 * The existing YakFlow SnapshotPendingSplitsState wire format is preserved until a separate
 * checkpoint migration can be validated. Debezium 2.7 offsets remain reader-owned.
 */
public final class MySqlSnapshotSplitAssigner implements MySqlSplitAssigner {
    private static final int MAX_SPLITS = 10_000;

    private final Deque<MySqlSnapshotSplit> remainingSplits = new ArrayDeque<>();
    private final Map<String, MySqlSnapshotSplit> assignedSplits = new LinkedHashMap<>();
    private final Map<String, FinishedSnapshotSplitInfo> finishedSplitInfos = new LinkedHashMap<>();
    private int totalSnapshotSplits;
    private boolean snapshotPlanned;
    private int nextTableIndex;
    private int nextChunkIndex;
    private ChunkRange nextChunkRange;
    private boolean planning;
    private boolean closed;

    public MySqlSnapshotSplitAssigner() {}

    public MySqlSnapshotSplitAssigner(SnapshotPendingSplitsState restored) {
        Objects.requireNonNull(restored, "restored");
        remainingSplits.addAll(restored.getRemainingSplits());
        assignedSplits.putAll(restored.getAssignedSplits());
        restored.getFinishedSplitInfos().forEach(info -> finishedSplitInfos.put(info.splitId(), info));
        totalSnapshotSplits = restored.totalSnapshotSplits();
        snapshotPlanned = restored.snapshotPlanned();
        nextTableIndex = restored.nextTableIndex();
        nextChunkIndex = restored.nextChunkIndex();
        nextChunkRange = restored.nextChunkRange();
    }

    @Override
    public void open() {}

    /**
     * Flink CDC-style asynchronous Chunk discovery owned by the Snapshot Assigner.
     *
     * <p>The coordinator supplies the existing async executor, while this assigner owns
     * discovery scheduling, outstanding-work gating and the durable ChunkSplitterState.
     */
    public boolean discoverNextAsync(
            List<TableId> tables,
            MySqlChunkSplitter splitter,
            SplitEnumeratorContext<MySqlSplit> context,
            int maxPendingChunks,
            BiConsumer<MySqlSnapshotSplit, Integer> onDiscovered,
            BiConsumer<TableId, Throwable> onError) {
        Objects.requireNonNull(tables, "tables");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(onDiscovered, "onDiscovered");
        Objects.requireNonNull(onError, "onError");
        if (closed || planning || snapshotPlanned || remainingSplits.size() >= maxPendingChunks) return false;
        int tableIndex = nextTableIndex;
        int chunkIndex = nextChunkIndex;
        if (tableIndex >= tables.size()) {
            throw new IllegalStateException("Incomplete Snapshot table discovery terminal marker");
        }
        if ((chunkIndex == 0) != (nextChunkRange == null)) {
            throw new IllegalStateException("Snapshot Chunk discovery cursor does not match its index");
        }
        TableId table = tables.get(tableIndex);
        ChunkSplitterState cursor = getChunkSplitterState();
        planning = true;
        try {
            context.callAsync(() -> splitter.planNext(table, tableIndex, cursor), (split, failure) -> {
                planning = false;
                if (closed) return;
                if (failure != null) {
                    onError.accept(table, failure);
                    return;
                }
                try {
                    appendPlan(split, tables.size());
                } catch (RuntimeException invalid) {
                    onError.accept(table, invalid);
                    return;
                }
                onDiscovered.accept(split, chunkIndex + 1);
            });
        } catch (RuntimeException schedulingFailure) {
            planning = false;
            throw schedulingFailure;
        }
        return true;
    }

    /** Registers all the supplied splits and finishes discovery. */
    public void plan(List<MySqlSnapshotSplit> splits) {
        append(splits, true);
    }

    /** Adds newly discovered snapshot splits without declaring the table scan complete. */
    public void append(List<MySqlSnapshotSplit> splits, boolean lastPage) {
        Objects.requireNonNull(splits, "splits");
        if (snapshotPlanned || (long) totalSnapshotSplits + splits.size() > MAX_SPLITS) {
            throw new IllegalStateException("Snapshot discovery is complete or exceeds the split limit");
        }
        Set<String> ids = new LinkedHashSet<>(assignedSplits.keySet());
        ids.addAll(finishedSplitInfos.keySet());
        remainingSplits.forEach(split -> ids.add(split.splitId()));
        for (MySqlSnapshotSplit split : splits) {
            requireSplit(split);
            if (!ids.add(split.splitId())) {
                throw new IllegalArgumentException("Duplicate snapshot split ID");
            }
        }
        remainingSplits.addAll(splits);
        totalSnapshotSplits += splits.size();
        snapshotPlanned = lastPage;
    }

    /**
     * Advances the checkpointed discovery cursor only after the discovered split has been
     * accepted. The cursor always denotes the next half-open Chunk boundary.
     */
    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        Objects.requireNonNull(split, "split");
        ChunkRange range = split.chunkRange();
        if (tableCount < 1 || nextTableIndex < 0 || nextTableIndex >= tableCount || range == null) {
            throw new IllegalStateException("Invalid snapshot discovery transition");
        }
        if ((nextChunkIndex == 0 && (nextChunkRange != null || range.start() != null))
                || (nextChunkIndex > 0
                        && (nextChunkRange == null
                                || nextChunkRange.end() != null
                                || !nextChunkRange.tableId().equals(range.tableId())
                                || !nextChunkRange.column().equals(range.column())
                                || nextChunkRange.type() != range.type()
                                || !Objects.equals(nextChunkRange.start(), range.start())))) {
            throw new IllegalStateException("Snapshot range differs from the durable discovery cursor");
        }
        boolean tableComplete = range.last();
        ChunkRange following =
                tableComplete ? null : new ChunkRange(range.tableId(), range.column(), range.type(), range.end(), null);
        append(List.of(split), tableComplete && nextTableIndex + 1 == tableCount);
        if (tableComplete) {
            nextTableIndex++;
            nextChunkIndex = 0;
        } else {
            nextChunkIndex++;
        }
        nextChunkRange = following;
    }

    @Override
    public Optional<MySqlSplit> getNext() {
        return Optional.ofNullable(next());
    }

    /** Assigning a split is never evidence that its reader has completed it. */
    public MySqlSnapshotSplit next() {
        MySqlSnapshotSplit split = remainingSplits.pollFirst();
        if (split != null) assignedSplits.put(split.splitId(), split);
        return split;
    }

    @Override
    public boolean waitingForFinishedSplits() {
        return !allFinished();
    }

    /**
     * Flink CDC exposes complete Binlog filtering metadata only after all snapshot splits
     * have finished; partial metadata is available separately for Reader ACKs.
     */
    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        if (waitingForFinishedSplits()) {
            throw new IllegalStateException("Snapshot splits have not all reported their high watermarks");
        }
        return finishedSplits();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> offsets) {
        Objects.requireNonNull(offsets, "offsets");
        // Validate an entire report before mutating any assignment state.
        List<FinishedSnapshotSplitInfo> accepted = new ArrayList<>();
        for (var entry : offsets.entrySet()) {
            accepted.add(finishedInfo(entry.getKey(), entry.getValue()));
        }
        accepted.forEach(this::finishSnapshot);
    }

    /** Builds completion metadata from coordinator-owned split boundaries. */
    public FinishedSnapshotSplitInfo finishSnapshot(String splitId, DebeziumOffset highWatermark) {
        FinishedSnapshotSplitInfo info = finishedInfo(splitId, highWatermark);
        finishSnapshot(info);
        return info;
    }

    private FinishedSnapshotSplitInfo finishedInfo(String splitId, DebeziumOffset highWatermark) {
        Objects.requireNonNull(splitId, "splitId");
        Objects.requireNonNull(highWatermark, "highWatermark");
        FinishedSnapshotSplitInfo previous = finishedSplitInfos.get(splitId);
        if (previous != null) {
            if (!previous.highWatermark().equals(highWatermark)) {
                throw new IllegalStateException("Conflicting completed snapshot high watermark");
            }
            return previous;
        }
        MySqlSnapshotSplit assigned = assignedSplits.get(splitId);
        if (assigned == null) throw new IllegalArgumentException("Unknown completed snapshot split");
        return new FinishedSnapshotSplitInfo(
                assigned.tableId(),
                splitId,
                assigned.lowerInclusive(),
                assigned.upperExclusive(),
                highWatermark,
                assigned.chunkRange());
    }

    /** Accepts one finished Chunk only after its ID, boundaries and watermark agree. */
    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        Objects.requireNonNull(info, "info");
        FinishedSnapshotSplitInfo previous = finishedSplitInfos.get(info.splitId());
        if (previous != null) {
            if (!previous.equals(info)) throw new IllegalStateException("Conflicting completed snapshot metadata");
            return;
        }
        MySqlSnapshotSplit assigned = assignedSplits.get(info.splitId());
        if (assigned == null
                || !assigned.tableId().equals(info.tableId())
                || !Objects.equals(assigned.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(assigned.upperExclusive(), info.upperExclusive())
                || !Objects.equals(assigned.chunkRange(), info.chunkRange())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        assignedSplits.remove(info.splitId());
        finishedSplitInfos.put(info.splitId(), info);
    }

    public void addBack(MySqlSnapshotSplit returned) {
        requireSplit(returned);
        MySqlSnapshotSplit original = assignedSplits.get(returned.splitId());
        if (original == null
                || !original.tableId().equals(returned.tableId())
                || !Objects.equals(original.chunkRange(), returned.chunkRange())
                || !Objects.equals(original.lowerInclusive(), returned.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), returned.upperExclusive())) {
            throw new IllegalArgumentException("Returned snapshot split was not assigned or its range changed");
        }
        // Incomplete snapshot output is never durable. A failed Reader rescans the original
        // immutable Chunk rather than persisting partially emitted row images in coordinator state.
        assignedSplits.remove(original.splitId());
        remainingSplits.addFirst(original);
    }

    @Override
    public void addSplits(Collection<MySqlSplit> splits) {
        for (MySqlSplit split : splits) {
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalArgumentException("Snapshot assigner only accepts snapshot splits");
            }
            addBack(snapshot);
        }
    }

    public boolean allFinished() {
        return snapshotPlanned
                && remainingSplits.isEmpty()
                && assignedSplits.isEmpty()
                && finishedSplitInfos.size() == totalSnapshotSplits;
    }

    public boolean planned() {
        return snapshotPlanned;
    }

    public int total() {
        return totalSnapshotSplits;
    }

    public List<MySqlSnapshotSplit> pending() {
        return List.copyOf(remainingSplits);
    }

    public Map<String, MySqlSnapshotSplit> assigned() {
        return Map.copyOf(assignedSplits);
    }

    public Set<String> finished() {
        return Set.copyOf(finishedSplitInfos.keySet());
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return List.copyOf(finishedSplitInfos.values());
    }

    public int nextTableIndex() {
        return nextTableIndex;
    }

    public int nextChunkIndex() {
        return nextChunkIndex;
    }

    public ChunkRange nextChunkRange() {
        return nextChunkRange;
    }

    public ChunkSplitterState getChunkSplitterState() {
        if (nextChunkRange == null) return ChunkSplitterState.NO_SPLITTING_TABLE_STATE;
        return new ChunkSplitterState(
                nextChunkRange.tableId(),
                ChunkSplitterState.ChunkBound.middleOf(nextChunkRange.start()),
                nextChunkIndex);
    }

    @Override
    public SnapshotPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) throw new IllegalArgumentException("Checkpoint ID must not be negative");
        return new SnapshotPendingSplitsState(
                snapshotPlanned,
                totalSnapshotSplits,
                pending(),
                assigned(),
                finished(),
                finishedSplits(),
                nextTableIndex,
                nextChunkIndex,
                nextChunkRange);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {}

    @Override
    public AssignerStatus getAssignerStatus() {
        return allFinished() ? AssignerStatus.INITIAL_ASSIGNING_FINISHED : AssignerStatus.INITIAL_ASSIGNING;
    }

    @Override
    public void startAssignNewlyAddedTables() {
        throw new UnsupportedOperationException("Dynamic table discovery has not been enabled");
    }

    @Override
    public boolean noMoreSplits() {
        return snapshotPlanned && remainingSplits.isEmpty();
    }

    @Override
    public void onBinlogSplitUpdated() {
        throw new UnsupportedOperationException("Snapshot assigner does not own a Binlog split");
    }

    @Override
    public void close() {
        closed = true;
    }

    private static void requireSplit(MySqlSnapshotSplit split) {
        Objects.requireNonNull(split, "split");
        if (split.splitId() == null || split.splitId().isBlank() || split.tableId() == null) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID and table");
        }
    }
}
