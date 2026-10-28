package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mailbox-owned SourceReader for parallel snapshot ranges and one deferred Binlog stream.
 *
 * <p>Finished but not acknowledged snapshot splits remain in Reader checkpoint state until
 * the Enumerator confirms receipt of their completion. The Binlog gate opens only through
 * a coordinator event after the snapshot handoff checkpoint completes.
 */
public final class MySqlSourceReader
        extends SingleThreadMultiplexSourceReaderBase<MySqlFetchedRecord, TableRecord, MySqlSplit, MySqlSplitState> {

    private final MySqlSplitReader splits;
    private final SchemaChangeHandler schemaChanges;
    private final TableMetrics metrics;
    private final long generation;
    private final boolean initial;
    private final Map<String, MySqlSnapshotSplit> finishedUnacked = new LinkedHashMap<>();
    private MySqlResumeBinlogEvent resumeEvent;
    private final BinlogSplitMetaGroupAssembler binlogMetadata = new BinlogSplitMetaGroupAssembler();
    private final java.util.Set<String> acknowledged = new java.util.HashSet<>();
    private final Map<String, Long> finishedCheckpoint = new LinkedHashMap<>();

    public MySqlSourceReader(
            MySqlSourceConfig config, SourceReaderContext context, TableMetrics metrics, boolean initial) {
        this(config, new MySqlSourceReaderContext(context), metrics, initial);
    }

    private MySqlSourceReader(
            MySqlSourceConfig config, MySqlSourceReaderContext readerContext, TableMetrics metrics, boolean initial) {
        this(
                new MySqlSplitReader(
                        config, metrics, readerContext.getSourceReaderContext().getIndexOfSubtask(), readerContext),
                readerContext,
                metrics,
                initial,
                sourceId(config, readerContext.getSourceReaderContext()));
    }

    private MySqlSourceReader(
            MySqlSplitReader splits,
            MySqlSourceReaderContext readerContext,
            TableMetrics metrics,
            boolean initial,
            String sourceId) {
        super(
                () -> splits,
                new MySqlRecordEmitter(readerContext, splits, metrics, sourceId),
                readerContext.getSourceReaderContext());
        this.schemaChanges = readerContext.schemaChanges();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.generation = metrics.generation();
        this.splits = splits;
        this.initial = initial;
    }

    /**
     * Bind event identity to a stable task execution, never to mutable split checkpoints.
     *
     * <p>The instance ID remains constant across full-job checkpoint restoration.
     * Direct connector callers without an execution ID use Debezium's logical source name.
     */
    private static String sourceId(MySqlSourceConfig config, SourceReaderContext context) {
        String executionId = context.getConfiguration().get(ExecutionOptions.LOG_ID);
        return executionId.isBlank() ? config.topicPrefix() : executionId + ":" + config.topicPrefix();
    }

    @Override
    public final InputStatus pollNext(ReaderOutput<TableRecord> output) throws Exception {
        return schemaChanges.tryCompleteSchemaChange() ? super.pollNext(output) : InputStatus.NOTHING_AVAILABLE;
    }

    @Override
    public final CompletableFuture<Void> isAvailable() {
        CompletableFuture<Void> pending = schemaChanges.getAvailableFuture();
        return pending == null ? super.isAvailable() : pending;
    }

    @Override
    public final void start() {
        context.sendSplitRequest();
    }

    @Override
    protected MySqlSplitState initializedState(MySqlSplit split) {
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        if (resumeEvent != null
                && split instanceof MySqlBinlogSplit binlog
                && binlog.phase() != MySqlBinlogSplit.Phase.STANDALONE) {
            state.resume(resumeEvent.highWatermark(), binlogMetadata.finishedSplits(resumeEvent.highWatermark()));
        }
        return state;
    }

    @Override
    protected MySqlSplit toSplitType(String splitId, MySqlSplitState state) {
        return state.checkpoint(state.binlog() ? splits.snapshotHistory() : null);
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlSplitState> finished) {
        Map<String, DebeziumOffset> finishedOffsets = new LinkedHashMap<>();
        for (var entry : finished.entrySet()) {
            MySqlSplit split = entry.getValue().checkpoint(null);
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalStateException("Continuous MySQL Binlog split cannot finish");
            }
            finishedUnacked.put(snapshot.splitId(), snapshot);
            finishedOffsets.put(
                    snapshot.splitId(),
                    Objects.requireNonNull(snapshot.finished(), "Finished chunk has no watermark")
                            .highWatermark());
        }
        if (!finishedOffsets.isEmpty()) {
            context.sendSourceEventToCoordinator(new FinishedSnapshotSplitsReportEvent(finishedOffsets));
            context.sendSplitRequest();
        }
    }

    private void reportFinishedSplits() {
        Map<String, DebeziumOffset> finishedOffsets = new LinkedHashMap<>();
        finishedUnacked.forEach((id, split) -> {
            if (split.finished() != null)
                finishedOffsets.put(id, split.finished().highWatermark());
        });
        if (!finishedOffsets.isEmpty()) {
            context.sendSourceEventToCoordinator(new FinishedSnapshotSplitsReportEvent(finishedOffsets));
        }
    }

    @Override
    public List<MySqlSplit> snapshotState(long checkpointId) {
        schemaChanges.requireNoPendingSchemaChange();
        List<MySqlSplit> active = new ArrayList<>(super.snapshotState(checkpointId));
        forEachActiveSplitState(state -> state.snapshotCheckpoint(checkpointId));
        acknowledged.forEach(id -> finishedCheckpoint.putIfAbsent(id, checkpointId));
        active.addAll(finishedUnacked.values());
        return List.copyOf(active);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        if (!initial) metrics.checkpointCompleted(generation, checkpointId, false);
        forEachActiveSplitState(state -> {
            state.completeCheckpoint(checkpointId);
        });
        finishedCheckpoint.entrySet().removeIf(entry -> {
            if (entry.getValue() > checkpointId) return false;
            finishedUnacked.remove(entry.getKey());
            acknowledged.remove(entry.getKey());
            return true;
        });
    }

    @Override
    public void handleSourceEvents(SourceEvent event) {
        if (!initial) throw new IllegalArgumentException("Binlog-only reader does not accept hybrid events");
        if (event instanceof FinishedSnapshotSplitsAckEvent ack) {
            ack.getFinishedSplits().forEach(id -> {
                if (finishedUnacked.containsKey(id)) acknowledged.add(id);
            });
        } else if (event instanceof FinishedSnapshotSplitsRequestEvent) {
            reportFinishedSplits();
        } else if (event instanceof BinlogSplitMetaEvent group) {
            if (!MySqlBinlogSplit.ID.equals(group.getSplitId())) {
                throw new IllegalArgumentException("Metadata delivered for another Binlog split");
            }
            if (binlogMetadata.addGroup(group)) {
                context.sendSourceEventToCoordinator(
                        new BinlogSplitMetaAssembledEvent(binlogMetadata.generation()));
            } else if (group.getBinlogAssignmentGeneration() == binlogMetadata.generation()) {
                context.sendSourceEventToCoordinator(
                        new BinlogSplitMetaRequestEvent(
                                binlogMetadata.nextGroupId(), binlogMetadata.generation()));
            }
        } else if (event instanceof MySqlResumeBinlogEvent resume) {
            List<FinishedSnapshotSplitInfo> finished = binlogMetadata.finishedSplits(resume.highWatermark());
            resumeEvent = resume;
            forEachActiveSplitState(state -> {
                if (state.binlog()) {
                    state.resume(resume.highWatermark(), finished);
                }
            });
            metrics.streaming(generation);
            splits.resumeBinlog(resume.highWatermark(), finished);
        } else if (event instanceof io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent) {
            splits.finishSnapshots();
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");
        }
    }

    @Override
    public void close() throws Exception {
        try {
            super.close();
        } finally {
            if (!initial) metrics.sourceClosed(generation);
        }
    }
}
