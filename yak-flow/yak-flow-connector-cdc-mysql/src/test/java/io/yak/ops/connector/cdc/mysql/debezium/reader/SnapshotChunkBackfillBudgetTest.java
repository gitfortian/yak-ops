package io.yak.ops.connector.cdc.mysql.debezium.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;

/** Documents the bounded-memory contract independently of Docker and Binlog timing. */
class SnapshotChunkBackfillBudgetTest {
    private static final TableId TABLE = new TableId("shop", null, "wide_rows");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("payload", LogicalTypes.varchar(32_000).copy(false))),
            List.of("id"));

    @Test
    void wideChunkMayExceedByteBudgetWhileRemainingBelowRowBudget() {
        SnapshotChunkBackfill buffer = new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null),
                SCHEMA,
                100_000,
                4L * 1024 * 1024);
        String payload = "x".repeat(20_000);
        // The configured 100000-row limit is not an assurance that a chunk
        // fits the current 4 MiB normalized image budget.
        assertThatThrownBy(() -> {
            for (int index = 0; index < 140; index++) {
                buffer.addSnapshot(new TableRecord(
                        TABLE, RowKind.INSERT, GenericRowData.of((long) index, payload)));
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot Backfill buffer limit exceeded");
        assertThatThrownBy(buffer::finish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot Backfill has failed");
    }

    @Test
    void defaultBoundedBackfillAcceptsRowsBeyondOldFourMiBLimit() {
        SnapshotChunkBackfill buffer = new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null),
                SCHEMA,
                100_000);
        String payload = "x".repeat(20_000);
        for (int index = 0; index < 180; index++) {
            buffer.addSnapshot(new TableRecord(
                    TABLE, RowKind.INSERT, GenericRowData.of((long) index, payload)));
        }
        // The same 180 wide rows exceed 4 MiB under the existing estimate.
        // They now fit the existing 64 MiB per-chunk bound.
        assertThat(buffer.finish()).hasSize(180);
    }

    @Test
    void defaultBoundStillRejectsAnOversizedChunkWithoutPartialPublication() {
        SnapshotChunkBackfill buffer = new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null),
                SCHEMA,
                100_000);
        String payload = "x".repeat(20_000);
        assertThatThrownBy(() -> {
            for (int index = 0; index < 2_000; index++) {
                buffer.addSnapshot(new TableRecord(
                        TABLE, RowKind.INSERT, GenericRowData.of((long) index, payload)));
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot Backfill buffer limit exceeded");
        assertThatThrownBy(buffer::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSmallChunkPublishesOnlyFinalNormalizedImages() {
        SnapshotChunkBackfill buffer = new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null),
                SCHEMA,
                100_000,
                4L * 1024 * 1024);
        buffer.addSnapshot(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "before")));
        buffer.addSnapshot(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "second")));
        assertThat(buffer.finish()).extracting(row -> row.row().getField(0)).containsExactly(1L, 2L);
        assertThatThrownBy(buffer::finish).isInstanceOf(IllegalStateException.class);
    }
}
