package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises recovery from a mailbox-delivered offset while Debezium has read ahead,
 * including a complete Schema History record newer than the saved offset.
 */
@Tag("cdc")
@Timeout(180)
class MySqlDebeziumRecoveryIT {

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-debezium-recovery", true, false);

    private static final TableId TABLE = new TableId("yak_cdc_it", null, "debezium_recovery");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64).copy(false))),
            List.of("id"));

    @BeforeEach
    void createFreshTable() throws SQLException {
        execute("DROP TABLE IF EXISTS debezium_recovery");
        execute("CREATE TABLE debezium_recovery "
                + "(id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL) ENGINE=InnoDB");
    }

    @Test
    void recoveryUsesMailboxOffsetAndReplaysRowsWhenEngineAndSchemaHistoryAreAhead() throws Exception {
        MySqlBinlogSplit anchor = captureInitialAnchor();
        MySqlBinlogSplit checkpoint;

        try (MySqlBinlogSplitReadTask firstAttempt = new MySqlBinlogSplitReadTask(config())) {
            firstAttempt.start(anchor);
            execute("INSERT INTO debezium_recovery (id,name) VALUES (1,'delivered')");
            BinlogEvent delivered = pollUntil(
                    firstAttempt,
                    event -> event.records().stream().anyMatch(row -> id(row) == 1L),
                    Duration.ofSeconds(60));

            insertRows(2, 20);
            // This DDL changes metadata history but not the row shape consumed by the
            // frozen source schema. Its history record is newer than the mailbox offset.
            execute("ALTER TABLE debezium_recovery ALTER COLUMN name SET DEFAULT 'later'");
            execute("INSERT INTO debezium_recovery (id) VALUES (99)");

            byte[] history = awaitReadAheadHistory(firstAttempt, delivered.offset());
            assertThat(firstAttempt.lastEngineOffset().compareTo(delivered.offset())).isPositive();
            assertThat(firstAttempt.lastQueuedOffset().compareTo(delivered.offset())).isPositive();
            assertThat(new String(history, StandardCharsets.UTF_8).toUpperCase(java.util.Locale.ROOT))
                    .contains("SET DEFAULT");
            assertThat(history[history.length - 1]).isEqualTo((byte) '\n');

            // The split state advances only when the mailbox emits a record. The
            // Engine and bounded queue can both be ahead without changing this offset.
            MySqlSplitState mailboxState = MySqlSplitState.fromSplit(anchor);
            mailboxState.emitBinlog(delivered.offset());
            MySqlBinlogSplit candidate = (MySqlBinlogSplit) mailboxState.checkpoint(history);
            byte[] bytes = MySqlSplitSerializer.INSTANCE.serialize(candidate);
            checkpoint = (MySqlBinlogSplit) MySqlSplitSerializer.INSTANCE.deserialize(
                    MySqlSplitSerializer.INSTANCE.getVersion(), bytes);
            assertThat(checkpoint.offset()).isEqualTo(delivered.offset());
            assertThat(checkpoint.schemaHistory()).isEqualTo(history);
        }

        // The history file contains a complete DDL record newer than this checkpoint.
        // Debezium must restore history only through the saved offset and replay the
        // later binlog range instead of treating Engine prefetch as durable progress.
        try (MySqlBinlogSplitReadTask restored = new MySqlBinlogSplitReadTask(config())) {
            restored.start(checkpoint);
            Set<Long> replayedIds = new HashSet<>();
            Map<Long, String> replayedNames = new java.util.LinkedHashMap<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (!replayedIds.contains(99L) && System.nanoTime() < deadline) {
                BinlogEvent event = restored.poll();
                if (event == null) continue;
                for (TableRecord row : event.records()) {
                    long id = id(row);
                    replayedIds.add(id);
                    replayedNames.put(id, row.row().getString(1));
                }
            }
            assertThat(replayedIds).containsAll(expectedIds(2, 20));
            assertThat(replayedIds).contains(99L);
            assertThat(replayedNames.get(99L)).isEqualTo("later");
        }
    }

    private MySqlBinlogSplit captureInitialAnchor() throws Exception {
        try (MySqlBinlogSplitReadTask bootstrap = new MySqlBinlogSplitReadTask(config())) {
            bootstrap.start(MySqlBinlogSplit.forInitialSnapshot());
            BinlogEvent anchor = pollUntil(
                    bootstrap, event -> event.records().isEmpty() && event.schemaChange() == null,
                    Duration.ofSeconds(60));
            byte[] history = bootstrap.snapshotHistory();
            assertThat(history).isNotEmpty();
            assertThat(history[history.length - 1]).isEqualTo((byte) '\n');
            return new MySqlBinlogSplit(anchor.offset(), history);
        }
    }

    private byte[] awaitReadAheadHistory(MySqlBinlogSplitReadTask task, DebeziumOffset delivered) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            byte[] history = task.snapshotHistory();
            boolean hasLaterDdl = new String(history, StandardCharsets.UTF_8)
                    .toUpperCase(java.util.Locale.ROOT)
                    .contains("SET DEFAULT");
            DebeziumOffset engine = task.lastEngineOffset();
            DebeziumOffset queued = task.lastQueuedOffset();
            if (hasLaterDdl
                    && engine != null
                    && engine.compareTo(delivered) > 0
                    && queued != null
                    && queued.compareTo(delivered) > 0) {
                return history;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Debezium did not read ahead through the later Schema History record");
    }

    private static BinlogEvent pollUntil(
            MySqlBinlogSplitReadTask task, Predicate<BinlogEvent> condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            BinlogEvent event = task.poll();
            if (event != null && condition.test(event)) return event;
        }
        throw new AssertionError("Timed out waiting for the expected Debezium Binlog event");
    }

    private void insertRows(long first, long last) throws SQLException {
        try (var connection = MYSQL.metadata().connect();
                var statement = connection.prepareStatement(
                        "INSERT INTO debezium_recovery (id,name) VALUES (?,?)")) {
            for (long id = first; id <= last; id++) {
                statement.setLong(1, id);
                statement.setString(2, "row-" + id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Set<Long> expectedIds(long first, long last) {
        Set<Long> ids = new HashSet<>();
        for (long id = first; id <= last; id++) ids.add(id);
        return ids;
    }

    private static long id(TableRecord row) {
        return ((Number) row.row().getField(0)).longValue();
    }

    private MySqlSourceConfig config() {
        var database = MYSQL.metadata();
        return MySqlSource.builder()
                .hostname(database.host())
                .port(database.port())
                .username(database.username())
                .password(database.password())
                .serverId(23_800)
                .topicPrefix("yak-debezium-recovery")
                .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                .table(TABLE, SCHEMA)
                .buildHybrid(32)
                .config();
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
