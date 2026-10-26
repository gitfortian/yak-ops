package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableRecord;

/**
 * Emits one complete Debezium change on a single mailbox step, then advances the source offset.
 *
 * <p>An UPDATE_BEFORE and UPDATE_AFTER pair is never separated by a checkpoint barrier.
 * If downstream delivery fails, the source offset remains at its previous value.
 */
public final class MySqlCdcRecordEmitter implements RecordEmitter<BinlogEvent, TableRecord, MySqlCdcSplitState> {

    private final SchemaChangeHandler schemaChanges;

    public MySqlCdcRecordEmitter(SchemaChangeHandler schemaChanges) {
        this.schemaChanges = java.util.Objects.requireNonNull(schemaChanges, "schemaChanges");
    }

    @Override
    public void emitRecord(BinlogEvent event, ReaderOutput<TableRecord> output, MySqlCdcSplitState state)
            throws Exception {
        if (event.schemaChange() != null) {
            schemaChanges.submitSchemaChange(event.schemaChange(), () -> state.onEmitted(event.offset()));
            return;
        }
        String eventPosition = MySqlChangeEventIds.fromBinlog(event.offset());
        int imageCount = event.records().size();
        for (int index = 0; index < imageCount; index++) {
            TableRecord record = event.records().get(index);
            ChangeEventId identity = eventPosition == null
                    ? null
                    : new ChangeEventId(state.sourceId(), eventPosition, index, imageCount);
            output.collect(identity == null ? record : record.withChangeEventId(identity));
        }
        state.onEmitted(event.offset());
    }
}
