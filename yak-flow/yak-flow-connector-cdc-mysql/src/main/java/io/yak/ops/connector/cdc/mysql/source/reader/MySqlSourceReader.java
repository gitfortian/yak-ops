package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Receives unbounded MySQL Binlog changes on the mailbox and checkpoints delivered offsets.
 *
 * <p>All Debezium I/O is owned by the background SplitReader and never touches the Runtime
 * state backend. This reader emits an entire UPDATE pair before advancing progress.
 */
public final class MySqlSourceReader
        extends SingleThreadMultiplexSourceReaderBase<BinlogEvent, TableRecord, MySqlBinlogSplit, MySqlCdcSplitState> {

    private final MySqlCdcSplitReader splitReader;
    private final MySqlSchemaChangeHandler schemaChanges;

    public MySqlSourceReader(MySqlCdcSourceConfig config, String fingerprint, SourceReaderContext context) {
        this(new MySqlCdcSplitReader(config, fingerprint), context, new MySqlSchemaChangeHandler(context));
    }

    private MySqlSourceReader(
            MySqlCdcSplitReader splitReader, SourceReaderContext context, MySqlSchemaChangeHandler schemaChanges) {
        super(() -> splitReader, new MySqlCdcRecordEmitter(schemaChanges), context);
        this.splitReader = splitReader;
        this.schemaChanges = schemaChanges;
    }

    @Override
    public InputStatus pollNext(ReaderOutput<TableRecord> output) throws Exception {
        return schemaChanges.tryCompleteSchemaChange() ? super.pollNext(output) : InputStatus.NOTHING_AVAILABLE;
    }

    @Override
    public CompletableFuture<Void> isAvailable() {
        CompletableFuture<Void> pending = schemaChanges.getAvailableFuture();
        return pending == null ? super.isAvailable() : pending;
    }

    @Override
    public List<MySqlBinlogSplit> snapshotState(long checkpointId) {
        schemaChanges.requireNoPendingSchemaChange();
        return super.snapshotState(checkpointId);
    }

    @Override
    public void start() {
        context.sendSplitRequest();
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
