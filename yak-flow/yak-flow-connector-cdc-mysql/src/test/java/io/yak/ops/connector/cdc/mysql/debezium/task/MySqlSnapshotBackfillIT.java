package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises the real MySQL read view and Debezium LOW/HIGH bounded backfill.
 *
 * <p>The phase callbacks commit on a separate JDBC connection at deterministic
 * boundaries, instead of racing the snapshot thread with arbitrary sleeps.
 * This is a Source contract test; a completed checkpoint and transactional Sink
 * are covered independently by the runtime and backend acceptance suites.
 */
@Tag("cdc")
@Timeout(180)
class MySqlSnapshotBackfillIT {
    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-snapshot-backfill", true, false);

    private static final TableId TABLE = new TableId("yak_cdc_it", null, "snapshot_contract");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64).copy(false))),
            List.of("id"));

    @BeforeEach
    void createFreshTable() throws SQLException {
        execute("DROP TABLE IF EXISTS snapshot_contract");
        execute("CREATE TABLE snapshot_contract "
                + "(id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL) ENGINE=InnoDB");
    }

    @Test
    void reconcilesCommitsBetweenLowAndReadViewAndBetweenSnapshotAndHigh() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'before'),(10,'deleted')");

        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(),
                0,
                () -> {
                    execute("UPDATE snapshot_contract SET name='updated' WHERE id=1");
                    execute("DELETE FROM snapshot_contract WHERE id=10");
                    execute("INSERT INTO snapshot_contract VALUES (3,'after')");
                    return null;
                },
                () -> {
                    // Visible in the consistent scan and also in (LOW, HIGH].
                    execute("INSERT INTO snapshot_contract VALUES (2,'low-gap')");
                    return null;
                },
                () -> null)) {
            MySqlSnapshotSplit prepared = reader.read(split(null, null));
            assertThat(prepared.output()).isNotNull();
            assertThat(prepared.output().high().compareTo(prepared.output().low())).isGreaterThan(0);
            assertThat(values(prepared)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(1L, "updated", 2L, "low-gap", 3L, "after"));
        }
    }

    @Test
    void reconcilesPrimaryKeyMovesAcrossHalfOpenChunkBounds() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (8,'outside'),(12,'inside')");

        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(), 0, () -> {
                    execute("UPDATE snapshot_contract SET id=22 WHERE id=12");
                    execute("UPDATE snapshot_contract SET id=13 WHERE id=8");
                    return null;
                })) {
            MySqlSnapshotSplit prepared = reader.read(split(10L, 20L));
            assertThat(values(prepared)).containsExactlyInAnyOrderEntriesOf(Map.of(13L, "outside"));
        }
    }

    @Test
    void failureAfterScanCannotPublishPartiallyReconciledRows() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'before')");

        MySqlSnapshotSplit assigned = split(null, null);
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(), 0, () -> {
                    throw new IllegalStateException("injected failure after JDBC scan");
                })) {
            assertThatThrownBy(() -> reader.read(assigned))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("injected failure");
        }
        // A failed attempt does not mutate the assigned split or publish a finished fence.
        assertThat(assigned.prepared()).isFalse();
        assertThat(assigned.finished()).isNull();
        assertThat(assigned.output()).isNull();

        // The full range can be scanned again after the failed attempt is closed.
        try (MySqlSnapshotSplitReadTask restored = new MySqlSnapshotSplitReadTask(config(), 0)) {
            assertThat(values(restored.read(assigned))).containsExactlyInAnyOrderEntriesOf(
                    Map.of(1L, "before"));
        }
    }

    private static MySqlSnapshotSplit split(Long start, Long end) {
        return MySqlSnapshotSplit.typed(
                "snapshot-contract-" + (start == null ? "first" : start),
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, start, end));
    }

    private static Map<Long, String> values(MySqlSnapshotSplit prepared) {
        return prepared.output().rows().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.row().getField(0)).longValue(),
                        row -> row.row().getString(1)));
    }

    private static MySqlSourceConfig config() {
        var database = MYSQL.metadata();
        return MySqlSource.builder()
                .hostname(database.host())
                .port(database.port())
                .username(database.username())
                .password(database.password())
                .serverId(23_400)
                .topicPrefix("yak-snapshot-contract")
                .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                .table(TABLE, SCHEMA)
                .buildHybrid(32)
                .config();
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
