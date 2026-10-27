package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mailbox-owned SourceReader for parallel snapshot ranges and one deferred Binlog stream.
 *
 * <p>Finished but not acknowledged snapshot splits remain in Reader checkpoint state until
 * the Enumerator confirms receipt of their completion. The Binlog gate opens only through
 * a coordinator event after the snapshot handoff checkpoint completes.
 */
public final class MySqlSourceReader
        extends MySqlSourceReaderBase<MySqlFetchedRecord, MySqlSplit, MySqlSplitState> {

    private final MySqlSplitReader splits;
    private final boolean initial;
    private final Map<String, MySqlSnapshotSplit> finishedUnacked = new LinkedHashMap<>();
    private MySqlResumeBinlogEvent resumeEvent;
    private final java.util.Set<String> acknowledged = new java.util.HashSet<>();
    private final Map<String, Long> finishedCheckpoint = new LinkedHashMap<>();

    public MySqlSourceReader(MySqlCdcSourceConfig config, String fingerprint,
            SourceReaderContext context, TableMetrics metrics, boolean initial) {
        this(new MySqlSplitReader(config, metrics, context.getIndexOfSubtask(), fingerprint),
                context, metrics, initial);
    }

    private MySqlSourceReader(MySqlSplitReader splits, SourceReaderContext context,
            TableMetrics metrics, boolean initial) {
        this(splits, context, metrics, new SchemaChangeHandler(context), initial);
    }

    private MySqlSourceReader(MySqlSplitReader splits, SourceReaderContext context,
            TableMetrics metrics, SchemaChangeHandler schemaChanges, boolean initial) {
        super(() -> splits, new MySqlRecordEmitter(context, splits, metrics, schemaChanges),
                context, schemaChanges, metrics);
        this.splits = splits;
        this.initial = initial;
    }

    @Override
    protected MySqlSplitState initializedState(MySqlSplit split) {
        MySqlSplitState state = new MySqlSplitState(split);
        if (resumeEvent != null && split instanceof MySqlHybridBinlogSplit) {
            state.resume(resumeEvent.highWatermark(), resumeEvent.finishedSplits());
        }
        return state;
    }

    @Override
    protected MySqlSplit toSplitType(String splitId, MySqlSplitState state) {
        return state.checkpoint(state.binlog() ? splits.snapshotHistory() : null);
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlSplitState> finished) {
        for (var entry : finished.entrySet()) {
            MySqlSplit split = entry.getValue().checkpoint(null);
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalStateException("Continuous MySQL Binlog split cannot finish");
            }
            finishedUnacked.put(snapshot.splitId(), snapshot);
            context.sendSourceEventToCoordinator(new MySqlSnapshotFinishedEvent(
                    Objects.requireNonNull(snapshot.finished(), "Finished chunk has no watermark")));
            context.sendSplitRequest();
        }
    }

    @Override
    public List<MySqlSplit> snapshotState(long checkpointId) {
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
            String id = state.completeCheckpoint(checkpointId);
            if (id != null) splits.releaseSnapshot(id);
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
        if (event instanceof MySqlSnapshotFinishedAckEvent ack) {
            if (finishedUnacked.containsKey(ack.splitId())) acknowledged.add(ack.splitId());
        } else if (event instanceof MySqlResumeBinlogEvent resume) {
            resumeEvent = resume;
            forEachActiveSplitState(state -> {
                if (state.binlog()) {
                    state.resume(resume.highWatermark(), resume.finishedSplits());
                }
            });
            metrics.streaming(generation);
            splits.resumeBinlog(resume);
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
