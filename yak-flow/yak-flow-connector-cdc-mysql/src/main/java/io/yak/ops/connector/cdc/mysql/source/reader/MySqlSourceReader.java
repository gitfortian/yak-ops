package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.Map;

/**
 * Receives unbounded MySQL Binlog changes on the mailbox and checkpoints delivered offsets.
 *
 * <p>All Debezium I/O is owned by the background SplitReader and never touches the Runtime
 * state backend. This reader emits an entire UPDATE pair before advancing progress.
 */
public final class MySqlSourceReader
        extends MySqlSourceReaderBase<BinlogEvent, MySqlBinlogSplit, MySqlCdcSplitState> {

    private final MySqlCdcSplitReader splitReader;

    public MySqlSourceReader(MySqlCdcSourceConfig config, String fingerprint, SourceReaderContext context) {
        this(config, fingerprint, context, new TableMetrics());
    }

    public MySqlSourceReader(
            MySqlCdcSourceConfig config, String fingerprint, SourceReaderContext context, TableMetrics metrics) {
        this(new MySqlCdcSplitReader(config, fingerprint), context, new SchemaChangeHandler(context), metrics);
    }

    private MySqlSourceReader(
            MySqlCdcSplitReader splitReader,
            SourceReaderContext context,
            SchemaChangeHandler schemaChanges,
            TableMetrics metrics) {
        super(
                () -> splitReader,
                new MySqlCdcRecordEmitter(schemaChanges, metrics),
                context,
                schemaChanges,
                metrics);
        this.splitReader = splitReader;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        metrics.checkpointCompleted(generation, checkpointId, false);
    }

    @Override
    public void close() throws Exception {
        try {
            super.close();
        } finally {
            metrics.sourceClosed(generation);
        }
    }

    @Override
    protected MySqlCdcSplitState initializedState(MySqlBinlogSplit split) {
        return new MySqlCdcSplitState(split);
    }

    @Override
    protected MySqlBinlogSplit toSplitType(String splitId, MySqlCdcSplitState state) {
        if (!MySqlBinlogSplit.ID.equals(splitId)) {
            throw new IllegalArgumentException("Unexpected MySQL Binlog split ID");
        }
        return state.checkpoint(splitReader.snapshotHistory());
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlCdcSplitState> finished) {
        throw new IllegalStateException("A continuous MySQL Binlog split must not finish normally");
    }
}
