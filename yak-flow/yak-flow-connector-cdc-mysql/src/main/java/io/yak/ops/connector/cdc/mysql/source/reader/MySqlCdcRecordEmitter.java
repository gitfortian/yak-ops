package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.data.TableRecord;

/**
 * Emits one complete Debezium change on a single mailbox step, then advances the source offset.
 *
 * <p>An UPDATE_BEFORE and UPDATE_AFTER pair is never separated by a checkpoint barrier.
 * If downstream delivery fails, the source offset remains at its previous value.
 */
public final class MySqlCdcRecordEmitter implements RecordEmitter<BinlogEvent, TableRecord, MySqlCdcSplitState> {

    private final MySqlSchemaChangeGate schemaGate;

    public MySqlCdcRecordEmitter() {
        this.schemaGate = null;
    }

    public MySqlCdcRecordEmitter(MySqlSchemaChangeGate schemaGate) {
        this.schemaGate = java.util.Objects.requireNonNull(schemaGate, "schemaGate");
    }

    @Override
    public void emitRecord(BinlogEvent event, ReaderOutput<TableRecord> output, MySqlCdcSplitState state)
            throws Exception {
        if (event.schemaChange() != null) {
            if (schemaGate == null) {
                throw new UnsupportedOperationException("MySQL CDC DDL requires a schema coordinator");
            }
            schemaGate.begin(event.schemaChange(), () -> state.onEmitted(event.offset()));
            return;
        }
        for (TableRecord record : event.records()) {
            output.collect(record);
        }
        state.onEmitted(event.offset());
    }
}
