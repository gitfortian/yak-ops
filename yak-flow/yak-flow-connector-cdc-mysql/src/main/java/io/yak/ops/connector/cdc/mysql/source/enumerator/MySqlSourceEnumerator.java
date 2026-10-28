package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlBinlogSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates bounded progressive chunk discovery, durable completion metadata, and the Binlog handoff. */
public final class MySqlSourceEnumerator implements SplitEnumerator<MySqlSplit, PendingSplitsState> {
    private static final int MAX_PENDING_CHUNKS = 32;
    private final SplitEnumeratorContext<MySqlSplit> context;
    private final MySqlChunkSplitter splitter;
    private final MySqlHybridSplitAssigner assigner;
    private final MySqlBinlogSplitAssigner streamAssigner;
    private final boolean initial;
    private final List<TableId> tables;
    private final Set<Integer> waitingReaders = new LinkedHashSet<>();
    private final TableMetrics metrics;
    private final long generation;
    private final boolean restoredHandoff;
    private boolean started;
    private boolean closed;
    private boolean planning;

    public MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored) {
        this(startupOptions, context, config, chunkSize, restored, new TableMetrics());
    }

    public MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored,
            TableMetrics metrics) {
        this(
                startupOptions,
                context,
                config,
                chunkSize,
                restored,
                metrics,
                startupOptions.startupMode() == StartupMode.INITIAL ? new MySqlChunkSplitter(config, chunkSize) : null);
    }

    /** Sets up the mode-specific assigner behind the single Source coordinator. */
    MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored,
            TableMetrics metrics,
            MySqlChunkSplitter planner) {
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(startupOptions, "startupOptions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        tables = List.copyOf(config.schemas().keySet());
        this.initial = startupOptions.startupMode() == StartupMode.INITIAL;
        if (initial && restored != null && !(restored instanceof HybridPendingSplitsState)) {
            throw new IllegalArgumentException("INITIAL cannot restore Binlog-only checkpoint state");
        }
        if (!initial && restored != null && !(restored instanceof BinlogPendingSplitsState)) {
            throw new IllegalArgumentException("Stream-only cannot restore snapshot checkpoint state");
        }
        HybridPendingSplitsState restoredHybrid = initial ? (HybridPendingSplitsState) restored : null;
        if (restoredHybrid != null && restoredHybrid.getSnapshotPendingSplits().nextChunkRange() != null) {
            int index = restoredHybrid.getSnapshotPendingSplits().nextTableIndex();
            if (index >= tables.size()) {
                throw new IllegalArgumentException("Restored typed chunk cursor references an invalid table");
            }
            TableId table = tables.get(index);
            var selected = ChunkUtils.getChunkKeyColumn(
                    config.schemas().get(table), config.chunkKeyColumns().get(table));
            ChunkRange cursor = restoredHybrid.getSnapshotPendingSplits().nextChunkRange();
            if (!cursor.tableId().equals(table)
                    || !cursor.column().equals(selected.name())
                    || cursor.type() != selected.dataType().getTypeRoot()) {
                throw new IllegalArgumentException("Restored typed chunk cursor differs from the selected table");
            }
        }
        tables.forEach(metrics::register);
        generation = metrics.beginSnapshot(restored != null);
        splitter = planner;
        assigner = initial ? new MySqlHybridSplitAssigner(startupOptions, restoredHybrid) : null;
        streamAssigner =
                initial ? null : new MySqlBinlogSplitAssigner(startupOptions, (BinlogPendingSplitsState) restored);
        restoredHandoff = restoredHybrid != null && restoredHybrid.phase() == HybridPendingSplitsState.Phase.HANDOFF;
        if (context.currentParallelism() < 1
                || context.currentParallelism() > (initial ? 16 : 1)
                || (initial && assigner.nextTableIndex() > tables.size())) {
            throw new IllegalArgumentException("Invalid MySQL Hybrid parallelism or discovery position");
        }
        if (restoredHybrid != null) {
            boolean complete = restoredHandoff || restoredHybrid.phase() == HybridPendingSplitsState.Phase.STREAMING;
            metrics.phase(
                    generation,
                    complete
                            ? TableMetrics.Phase.HANDOFF
                            : restoredHybrid.phase() == HybridPendingSplitsState.Phase.SNAPSHOT
                                    ? TableMetrics.Phase.SNAPSHOT
                                    : TableMetrics.Phase.INITIALIZING,
                    complete);
            Map<TableId, Integer> counts = new LinkedHashMap<>();
            restoredHybrid
                    .getSnapshotPendingSplits()
                    .getRemainingSplits()
                    .forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restoredHybrid
                    .getSnapshotPendingSplits()
                    .getAssignedSplits()
                    .values()
                    .forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restoredHybrid.getSnapshotPendingSplits().getFinishedSplitInfos().forEach(info -> {
                counts.merge(info.tableId(), 1, Integer::sum);
                metrics.splitFinished(generation, info.splitId(), info.tableId());
            });
            for (int i = 0; i < tables.size(); i++) {
                if (restoredHybrid.getSnapshotPendingSplits().snapshotPlanned()
                        || i < restoredHybrid.getSnapshotPendingSplits().nextTableIndex()) {
                    metrics.planned(generation, tables.get(i), counts.getOrDefault(tables.get(i), 0));
                }
            }
        }
    }

    @Override
    public void start() {
        if (started || closed) throw new IllegalStateException("MySQL Hybrid enumerator cannot start twice");
        started = true;
        if (initial) assigner.open();
        else streamAssigner.open();
        if (!initial) return;
        if (restoredHandoff) {
            assigner.resumeRestoredHandoff();
            sendResumeEvents();
        } else if (assigner.snapshotComplete()) enterHandoff();
        else planSnapshotAsync();
    }

    @Override
    public void handleSplitRequest(int reader, String requesterHostname) {
        if (closed) return;
        if (!started || !context.registeredReaders().containsKey(reader))
            throw new IllegalStateException("Unregistered Hybrid reader");
        if (!initial) {
            if (reader != 0) throw new IllegalArgumentException("Binlog-only source requires reader zero");
            MySqlBinlogSplit next = (MySqlBinlogSplit) streamAssigner.getNext().orElse(null);
            if (next != null) context.assignSplit(next, 0);
            return;
        }
        waitingReaders.add(reader);
        assignAvailable();
        planSnapshotAsync();
    }

    @Override
    public void addReader(int reader) {
        if (closed) return;
        if (!initial) return;
        if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING) sendResumeEvent(reader);
        else if (assigner.phase() == HybridPendingSplitsState.Phase.SNAPSHOT)
            context.sendEventToSourceReader(reader, new FinishedSnapshotSplitsRequestEvent());
        assignAvailable();
    }

    @Override
    public void handleSourceEvent(int reader, SourceEvent event) {
        if (closed) return;
        if (!initial) throw new IllegalArgumentException("Binlog-only coordinator does not receive snapshot events");
        if (event instanceof MySqlLowWatermarkEvent low) {
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may report bootstrap");
            boolean bootstrap = assigner.phase() == HybridPendingSplitsState.Phase.BOOTSTRAP;
            assigner.captureLow(low.offset());
            if (!bootstrap) return;
            metrics.phase(generation, TableMetrics.Phase.SNAPSHOT, false);
            planSnapshotAsync();
        } else if (event instanceof FinishedSnapshotSplitsReportEvent finished) {
            assigner.onFinishedSplits(finished.getFinishedOffsets());
            List<String> acknowledged = new ArrayList<>();
            Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
            assigner.getFinishedSplitInfos().forEach(info -> completed.put(info.splitId(), info));
            for (String splitId : finished.getFinishedOffsets().keySet()) {
                FinishedSnapshotSplitInfo info = Objects.requireNonNull(
                        completed.get(splitId), "Completed snapshot metadata must be available after reporting");
                metrics.splitFinished(generation, info.splitId(), info.tableId());
                acknowledged.add(info.splitId());
            }
            if (!acknowledged.isEmpty()) {
                context.sendEventToSourceReader(reader, new FinishedSnapshotSplitsAckEvent(acknowledged));
            }
            if (assigner.snapshotComplete()) enterHandoff();
            assignAvailable();
            planSnapshotAsync();
        } else throw new IllegalArgumentException("Unsupported MySQL Hybrid source event");
    }

    @Override
    public void addSplitsBack(List<MySqlSplit> splits, int reader) {
        if (closed || reader < 0 || reader >= context.currentParallelism())
            throw new IllegalStateException("Invalid split return");
        if (initial) {
            for (MySqlSplit split : splits) {
                if (split instanceof MySqlBinlogSplit && reader != 0) {
                    throw new IllegalArgumentException("Binlog belongs to reader zero");
                }
            }
            assigner.addSplits(splits);
            assignAvailable();
        } else {
            if (reader != 0) throw new IllegalArgumentException("Binlog belongs to reader zero");
            streamAssigner.addSplits(splits);
        }
    }

    @Override
    public PendingSplitsState snapshotState(long checkpointId) {
        if (!started || closed) throw new IllegalStateException("Inactive MySQL Hybrid enumerator");
        return initial ? assigner.snapshotState(checkpointId) : streamAssigner.snapshotState(checkpointId);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        boolean waitingForHandoff = initial && assigner.phase() == HybridPendingSplitsState.Phase.HANDOFF;
        if (initial) assigner.notifyCheckpointComplete(checkpointId);
        else streamAssigner.notifyCheckpointComplete(checkpointId);
        boolean handoff = waitingForHandoff && assigner.phase() == HybridPendingSplitsState.Phase.STREAMING;
        metrics.checkpointCompleted(generation, checkpointId, handoff);
        if (handoff) sendResumeEvents();
    }

    @Override
    public void close() {
        closed = true;
        if (splitter != null) splitter.cancel();
        if (assigner != null) assigner.close();
        if (streamAssigner != null) streamAssigner.close();
        metrics.sourceClosed(generation);
        waitingReaders.clear();
    }

    private void planSnapshotAsync() {
        if (!started
                || closed
                || planning
                || assigner.snapshotPlanned()
                || assigner.phase() != HybridPendingSplitsState.Phase.SNAPSHOT
                || assigner.pendingSnapshotCount() >= MAX_PENDING_CHUNKS) return;
        int tableIndex = assigner.nextTableIndex();
        int chunkIndex = assigner.nextChunkIndex();
        if (tableIndex >= tables.size()) throw new IllegalStateException("Incomplete discovery terminal marker");
        TableId table = tables.get(tableIndex);
        ChunkRange cursor = assigner.nextChunkRange();
        if ((chunkIndex == 0) != (cursor == null)) {
            throw new IllegalStateException("Typed chunk discovery cursor does not match its index");
        }
        planning = true;
        context.callAsync(() -> splitter.planNext(table, tableIndex, chunkIndex, cursor), (split, failure) -> {
            planning = false;
            if (closed) return;
            if (failure != null) {
                metrics.readFailed(generation, table, false);
                throw new IllegalStateException("MySQL snapshot chunk discovery failed", failure);
            }
            boolean tableComplete = split.chunkRange().last();
            assigner.appendPlan(split, tables.size());
            // A table's final split count is only known after the terminal range.
            if (tableComplete) metrics.planned(generation, table, chunkIndex + 1);
            assignAvailable();
            planSnapshotAsync();
        });
    }

    private void enterHandoff() {
        var high = assigner.finishedSplits().stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(DebeziumOffset::compareTo)
                .orElseThrow();
        assigner.captureHigh(high);
        metrics.phase(generation, TableMetrics.Phase.HANDOFF, false);
    }

    private void assignAvailable() {
        if (closed || !started) return;
        MySqlBinlogSplit binlog = assigner.nextBinlog();
        if (binlog != null) {
            if (context.registeredReaders().containsKey(0)) context.assignSplit(binlog, 0);
            else assigner.addBack(binlog);
            waitingReaders.remove(0);
        }
        if (assigner.phase() != HybridPendingSplitsState.Phase.SNAPSHOT) return;
        for (int reader : List.copyOf(waitingReaders)) {
            if (!context.registeredReaders().containsKey(reader)) {
                waitingReaders.remove(reader);
                continue;
            }
            MySqlSplit split = assigner.getNext().orElse(null);
            if (split == null) break;
            context.assignSplit(split, reader);
            waitingReaders.remove(reader);
        }
    }

    private void sendResumeEvents() {
        context.registeredReaders().keySet().forEach(this::sendResumeEvent);
    }

    private void sendResumeEvent(int reader) {
        context.sendEventToSourceReader(
                reader,
                reader == 0
                        ? new MySqlResumeBinlogEvent(assigner.high(), assigner.finishedSplits())
                        : new MySqlStopSnapshotEvent());
    }
}
