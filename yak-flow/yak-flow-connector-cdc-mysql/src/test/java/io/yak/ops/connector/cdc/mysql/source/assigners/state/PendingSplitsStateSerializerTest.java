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

    @Test
    void checksumProtectsTheEntireEnumeratorCheckpointAndRejectsMismatchedVersion() throws Exception {
        TableId table = new TableId("shop", null, "orders");
        MySqlSnapshotSplit pending = new MySqlSnapshotSplit("orders-0", table, 1L, 100L, null);
        SnapshotPendingSplitsState snapshot = new SnapshotPendingSplitsState(
                false, 1, List.of(pending), Map.of(), Set.of(), List.of(), 0, 0, null,
                List.of(), List.of(table),
                io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus.INITIAL_ASSIGNING,
                true, true, false);
        HybridPendingSplitsState original = new HybridPendingSplitsState(
                snapshot, true, null, HybridPendingSplitsState.Phase.BOOTSTRAP, null, null);
        byte[] protectedState = serializer.serialize(original);
        assertThat(serializer.getVersion()).isEqualTo(11);
        assertThat(serializer.deserialize(11, protectedState)).isEqualTo(original);

        byte[] changed = protectedState.clone();
        changed[16] ^= 0x01;
        assertThatThrownBy(() -> serializer.deserialize(11, changed))
                .isInstanceOf(IOException.class).hasMessageContaining("checksum");
        byte[] damagedTail = protectedState.clone();
        damagedTail[damagedTail.length - 1] ^= 0x01;
        assertThatThrownBy(() -> serializer.deserialize(11, damagedTail))
                .isInstanceOf(IOException.class).hasMessageContaining("checksum");
        assertThatThrownBy(() -> serializer.deserialize(10, protectedState))
                .isInstanceOf(IOException.class);
    }

    @Test
    void versionTenCheckpointRestoresWithoutChecksumAndUpgradesOnNextWrite() throws Exception {
        TableId table = new TableId("shop", null, "orders");
        MySqlSnapshotSplit split = new MySqlSnapshotSplit("orders-0", table, null, null, null);
        SnapshotPendingSplitsState snapshot = new SnapshotPendingSplitsState(
                false, 1, List.of(split), Map.of(), Set.of(), List.of(), 0, 0, null,
                List.of(), List.of(table),
                io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus.INITIAL_ASSIGNING,
                true, true, false);
        HybridPendingSplitsState original = new HybridPendingSplitsState(
                snapshot, true, null, HybridPendingSplitsState.Phase.BOOTSTRAP, null, null);
        // Version 11 intentionally leaves the old v10 payload unchanged, adding only CRC32C.
        byte[] version11 = serializer.serialize(original);
        byte[] version10 = Arrays.copyOf(version11, version11.length - Integer.BYTES);
        HybridPendingSplitsState restored =
                (HybridPendingSplitsState) serializer.deserialize(10, version10);
        assertThat(restored).isEqualTo(original);
        assertThat(serializer.deserialize(11, serializer.serialize(restored))).isEqualTo(original);
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
