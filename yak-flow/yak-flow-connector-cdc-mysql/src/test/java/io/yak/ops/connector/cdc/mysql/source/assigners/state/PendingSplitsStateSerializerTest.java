package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flink CDC-style contracts for enumerator checkpoint serialization.
 *
 * <p>The nested SnapshotPendingSplitsState owns snapshot progress; the hybrid state owns
 * only binlog handoff coordination. No obsolete source-definition fingerprint is serialized.
 */
class PendingSplitsStateSerializerTest {

    private final PendingSplitsStateSerializer serializer = new PendingSplitsStateSerializer();

    @Test
    void testsSerializeAndDeserialize() throws Exception {
        BinlogPendingSplitsState state = new BinlogPendingSplitsState(true, null);
        assertThat(roundTrip(state)).isEqualTo(state);
    }

    @Test
    void testSnapshotPendingStateSurvivesHybridRoundTrip() throws Exception {
        TableId table = new TableId("shop", null, "orders");
        MySqlSnapshotSplit remaining = new MySqlSnapshotSplit("orders-0", table, 100L, 200L, null);
        SnapshotPendingSplitsState snapshot = new SnapshotPendingSplitsState(
                true, 1, List.of(remaining), Map.of(), Set.of(), List.of(), 0, 0, null);
        HybridPendingSplitsState state = new HybridPendingSplitsState(
                snapshot, true, null, HybridPendingSplitsState.Phase.BOOTSTRAP, null, null);
        HybridPendingSplitsState restored = (HybridPendingSplitsState) roundTrip(state);
        assertThat(restored).isEqualTo(state);
        assertThat(restored.getSnapshotPendingSplits().getRemainingSplits()).containsExactly(remaining);
    }

    @Test
    void testFinishedSnapshotSplitInfosSurviveHandoffRoundTrip() throws Exception {
        TableId table = new TableId("shop", null, "orders");
        DebeziumOffset low = offset(100L);
        DebeziumOffset high = offset(200L);
        FinishedSnapshotSplitInfo finished = new FinishedSnapshotSplitInfo(
                table, "orders-0", null, null, high);
        SnapshotPendingSplitsState snapshot = new SnapshotPendingSplitsState(
                true, 1, List.of(), Map.of(), Set.of(finished.splitId()), List.of(finished), 0, 0, null);
        HybridPendingSplitsState state = new HybridPendingSplitsState(
                snapshot, true, null, HybridPendingSplitsState.Phase.HANDOFF, low, high);
        HybridPendingSplitsState restored = (HybridPendingSplitsState) roundTrip(state);
        assertThat(restored).isEqualTo(state);
        assertThat(restored.getSnapshotPendingSplits().getFinishedSplitInfos()).containsExactly(finished);
    }

    @Test
    void testCorruptedOrUnsupportedStateFailsClosed() throws Exception {
        byte[] bytes = serializer.serialize(new BinlogPendingSplitsState(true, null));
        assertThatThrownBy(() -> serializer.deserialize(serializer.getVersion() + 1, bytes))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, 3)))
                .isInstanceOf(IOException.class);
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        assertThatThrownBy(() -> serializer.deserialize(serializer.getVersion(), trailing))
                .isInstanceOf(IOException.class);
    }

    private PendingSplitsState roundTrip(PendingSplitsState state) throws IOException {
        byte[] bytes = serializer.serialize(state);
        return serializer.deserialize(serializer.getVersion(), bytes);
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"),
                Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
