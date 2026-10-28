package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlRecordEmitter;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReaderContext;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSplitReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void realMysqlWideChunkLargerThanFourMiBCompletesWithoutDroppingImages() throws Exception {
        execute("ALTER TABLE snapshot_contract MODIFY name VARCHAR(20000) NOT NULL");
        String payload = "w".repeat(12_000);
        try (var connection = MYSQL.metadata().connect();
                var statement = connection.prepareStatement(
                        "INSERT INTO snapshot_contract (id,name) VALUES (?,?)")) {
            for (int index = 0; index < 300; index++) {
                statement.setLong(1, index);
                statement.setString(2, payload);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        var wideSchema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(20_000).copy(false))),
                List.of("id"));
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(config(wideSchema), 0)) {
            MySqlSnapshotSplit prepared = reader.read(split(null, null));
            assertThat(prepared.output().rows()).hasSize(300);
            assertThat(prepared.output().rows())
                    .allSatisfy(row -> assertThat(row.row().getString(1)).isEqualTo(payload));
        }
    }

    @Test
    void failedMidChunkCollectionDoesNotCheckpointFinishedFenceAndCanReplay() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'first'),(2,'second')");
        var config = config();
        MySqlSnapshotSplit assigned = split(null, null);
        MySqlSnapshotSplit prepared;
        try (MySqlSnapshotSplitReadTask task = new MySqlSnapshotSplitReadTask(config, 0)) {
            prepared = task.read(assigned);
        }

        try (MySqlSplitReader splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()),
                    splitReader,
                    new TableMetrics(),
                    config.topicPrefix());
            MySqlSplitState incomplete = MySqlSplitState.fromSplit(assigned);
            List<TableRecord> delivered = new ArrayList<>();
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> emitter.emitRecord(
                    MySqlFetchedRecord.prepared(prepared),
                    row -> {
                        if (attempts.incrementAndGet() == 2) {
                            throw new IllegalStateException("injected collector failure");
                        }
                        delivered.add(row);
                    },
                    incomplete))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("injected collector failure");

            var serializer = MySqlSplitSerializer.INSTANCE;
            MySqlSnapshotSplit persisted = (MySqlSnapshotSplit) serializer.deserialize(
                    serializer.getVersion(), serializer.serialize(incomplete.checkpoint(null)));
            assertThat(persisted.finished()).isNull();
            assertThat(persisted.output()).isNull();
            assertThat(persisted.chunkRange()).isEqualTo(assigned.chunkRange());

            // A failed collector did not persist any normalized row images. A restored
            // reader must re-scan the original range, not resume at emitted ordinal 1.
            MySqlSnapshotSplit retried;
            try (MySqlSnapshotSplitReadTask restored = new MySqlSnapshotSplitReadTask(config, 0)) {
                retried = restored.read(persisted);
            }
            MySqlSplitState recovered = MySqlSplitState.fromSplit(persisted);
            List<TableRecord> replayed = new ArrayList<>();
            emitter.emitRecord(MySqlFetchedRecord.prepared(retried), replayed::add, recovered);
            assertThat(replayed).hasSize(2);
            assertThat(replayed.getFirst().changeEventId()).isEqualTo(
                    delivered.getFirst().changeEventId());
            MySqlSnapshotSplit finished = (MySqlSnapshotSplit) serializer.deserialize(
                    serializer.getVersion(), serializer.serialize(recovered.checkpoint(null)));
            assertThat(finished.finished()).isNotNull();
            assertThat(finished.output()).isNull();
        }
    }

    private static SourceReaderContext context() {
        return new SourceReaderContext() {
            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public int getIndexOfSubtask() {
                return 0;
            }

            @Override
            public int currentParallelism() {
                return 1;
            }

            @Override
            public void sendSplitRequest() {}
        };
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
        return config(SCHEMA);
    }

    private static MySqlSourceConfig config(TableSchema schema) {
        var database = MYSQL.metadata();
        return MySqlSource.builder()
                .hostname(database.host())
                .port(database.port())
                .username(database.username())
                .password(database.password())
                .serverId(23_400)
                .topicPrefix("yak-snapshot-contract")
                .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                .table(TABLE, schema)
                .buildHybrid(32)
                .config();
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
