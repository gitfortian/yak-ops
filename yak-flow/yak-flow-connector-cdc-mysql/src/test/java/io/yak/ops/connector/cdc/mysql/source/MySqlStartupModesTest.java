package io.yak.ops.connector.cdc.mysql.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlBinlogSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** All six Flink CDC 3.7 startup choices must reach a real Source/Assigner path. */
class MySqlStartupModesTest {
    @Test
    void initialAndSnapshotShareValidatedChunkPlanningButOnlySnapshotIsBounded() {
        MySqlSource initial = source(StartupOptions.initial());
        MySqlSource snapshot = source(StartupOptions.snapshot());
        assertThat(initial.getBoundedness()).isEqualTo(Boundedness.CONTINUOUS_UNBOUNDED);
        assertThat(snapshot.getBoundedness()).isEqualTo(Boundedness.BOUNDED);
        assertThat(snapshot.snapshotChunkSize()).isEqualTo(2);
        assertThat(snapshot.startupMode()).isEqualTo(StartupMode.SNAPSHOT);
    }

    @Test
    void allFourBinlogModesAssignOneRecoverableUnstartedSplit() throws Exception {
        for (StartupOptions options : List.of(
                StartupOptions.earliest(),
                StartupOptions.latest(),
                StartupOptions.specificOffset("mysql-bin.000001", 123),
                StartupOptions.timestamp(1_700_000_000_000L))) {
            MySqlSource source = source(options);
            assertThat(source.getBoundedness()).isEqualTo(Boundedness.CONTINUOUS_UNBOUNDED);
            assertThat(source.startupOptions()).isEqualTo(options);
            assertThatThrownBy(source::snapshotChunkSize).isInstanceOf(IllegalStateException.class);

            MySqlBinlogSplitAssigner assigner = new MySqlBinlogSplitAssigner(options, null);
            MySqlBinlogSplit split = (MySqlBinlogSplit) assigner.getNext().orElseThrow();
            assertThat(split.offset()).isNull();
            var serializer = new PendingSplitsStateSerializer(MySqlSplitSerializer.INSTANCE);
            BinlogPendingSplitsState checkpoint = (BinlogPendingSplitsState) assigner.snapshotState(1);
            var restored = new MySqlBinlogSplitAssigner(options, (BinlogPendingSplitsState)
                    serializer.deserialize(serializer.getVersion(), serializer.serialize(checkpoint)));
            assertThat(restored.getNext()).isEmpty();

            var unstarted = io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState.fromSplit(split);
            assertThat(unstarted.checkpoint(null)).isEqualTo(split);
        }
    }

    @Test
    void specificGtidStartupReachesBinlogAssigner() {
        StartupOptions options = StartupOptions.specificOffset("24bc7852-7e5c-11e6-8b50-080027d199c9:1-5");
        assertThat(source(options).startupOptions()).isEqualTo(options);
        assertThat(new MySqlBinlogSplitAssigner(options, null).getNext()).isPresent();
    }

    @Test
    void rejectsIncompleteHistoricalOffsetInsteadOfQuietlyUsingLatest() {
        assertThatThrownBy(() -> source(StartupOptions.specificOffset("", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Specific-offset startup");
        assertThatThrownBy(() -> source(StartupOptions.timestamp(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Timestamp startup");
    }

    private static MySqlSource source(StartupOptions options) {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        return MySqlSource.create(MySqlSource.builder()
                .hostname("localhost")
                .username("reader")
                .password("secret")
                .serverId(9751)
                .topicPrefix("startup-test")
                .table(new TableId("shop", null, "orders"), schema),
                options, 2, new TableMetrics());
    }
}
