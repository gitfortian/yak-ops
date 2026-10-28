package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.core.data.TableId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the Flink CDC incremental-snapshot chunk cursor model. */
class ChunkSplitterStateTest {

    @Test
    void holdsIndependentTableAndChunkPosition() {
        TableId table = new TableId("shop", null, "orders");
        ChunkSplitterState.ChunkBound bound = ChunkSplitterState.ChunkBound.middleOf(100L);
        ChunkSplitterState state = new ChunkSplitterState(table, bound, 3);
        ChunkSplitterState restored = new ChunkSplitterState(table,
                ChunkSplitterState.ChunkBound.middleOf(100L), 3);
        assertThat(state).isEqualTo(restored);
        assertThat(state.hashCode()).isEqualTo(restored.hashCode());
        assertThat(state.getCurrentSplittingTableId()).isEqualTo(table);
        assertThat(state.getNextChunkStart()).isEqualTo(bound);
        assertThat(state.getNextChunkId()).isEqualTo(3);
        assertThat(state).isNotEqualTo(new ChunkSplitterState(table, bound, 4));
        assertThat(ChunkSplitterState.ChunkBound.START_BOUND)
                .isNotEqualTo(ChunkSplitterState.ChunkBound.END_BOUND);
    }

    @Test
    void representsNoActiveSplitWithoutInventingAResumeCursor() {
        ChunkSplitterState state = ChunkSplitterState.NO_SPLITTING_TABLE_STATE;
        assertThat(state.getCurrentSplittingTableId()).isNull();
        assertThat(state.getNextChunkStart()).isNull();
        assertThat(state.getNextChunkId()).isNull();
        assertThat(state).isEqualTo(new ChunkSplitterState(null, null, null));
    }
}
