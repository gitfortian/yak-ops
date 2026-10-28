package io.yak.ops.connector.cdc.mysql.debezium.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Flink CDC RecordUtils.upsertBinlog contracts for normalized row images:
 * keyed rows replace by physical key; keyless rows retain full-image multiplicity.
 */
class SnapshotChunkBackfillMergeTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema KEYED = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("value", LogicalTypes.varchar(100))),
            List.of("id"));
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("value", LogicalTypes.varchar(100))),
            List.of());

    @Test
    void keyedUpdateAndDeleteMatchPhysicalPrimaryKeyUpsertSemantics() {
        var buffer = buffer(KEYED);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "old"));
        buffer.addSnapshot(row(RowKind.INSERT, 2, "deleted"));

        // Updating the physical key retracts the before image even when the
        // after image has a different key, unlike a blind new-key overwrite.
        buffer.apply(event(row(RowKind.UPDATE_BEFORE, 1, "old"),
                row(RowKind.UPDATE_AFTER, 3, "moved")));
        buffer.apply(event(row(RowKind.DELETE, 2, "deleted")));
        buffer.apply(event(row(RowKind.INSERT, 4, "inserted")));
        buffer.apply(event(row(RowKind.UPDATE_BEFORE, 3, "moved"),
                row(RowKind.UPDATE_AFTER, 3, "latest")));

        assertThat(buffer.finish())
                .extracting(record -> record.row().getField(1))
                .containsExactlyInAnyOrder("latest", "inserted");
    }

    @Test
    void keylessUpdateRemovesExactlyOneBeforeImageAndAddsOneAfterImage() {
        var buffer = buffer(KEYLESS);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "duplicate"));
        buffer.addSnapshot(row(RowKind.INSERT, 1, "duplicate"));
        buffer.addSnapshot(row(RowKind.INSERT, 1, "independent"));

        buffer.apply(event(row(RowKind.UPDATE_BEFORE, 1, "duplicate"),
                row(RowKind.UPDATE_AFTER, 1, "changed")));
        buffer.apply(event(row(RowKind.DELETE, 1, "duplicate")));
        assertThat(buffer.finish())
                .extracting(record -> record.row().getField(1))
                .containsExactlyInAnyOrder("independent", "changed");
    }

    @Test
    void keylessChunkKeyMutationFailsClosedInsteadOfClaimingExactlyOnce() {
        var buffer = buffer(KEYLESS);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "before"));

        // Flink CDC documents keyless chunk-key moves as a case that can lose
        // exactly-once semantics. Keep YakFlow's explicit rejection.
        assertThatThrownBy(() -> buffer.apply(event(
                        row(RowKind.UPDATE_BEFORE, 1, "before"),
                        row(RowKind.UPDATE_AFTER, 2, "after"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Chunk Key");
    }

    private static SnapshotChunkBackfill buffer(TableSchema schema) {
        return new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null), schema, 100);
    }

    private static TableRecord row(RowKind kind, long id, String value) {
        return new TableRecord(TABLE, kind, GenericRowData.of(id, value));
    }

    private static BinlogEvent event(TableRecord... records) {
        return new BinlogEvent(List.of(records), new DebeziumOffset(
                Map.of("server", "merge-tests"), Map.of("file", "mysql-bin.000001", "pos", 100)));
    }
}
