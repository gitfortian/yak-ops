package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSplitReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real Debezium 2.7 bootstrapping must not accidentally turn explicit offsets into latest. */
@Tag("cdc")
@Timeout(180)
class MySqlStartupModesIT {

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-startup-modes", true, false);

    private static final TableId TABLE = new TableId("yak_cdc_it", null, "startup_contract");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64).copy(false))),
            List.of("id"));

    @BeforeEach
    void reset() throws SQLException {
        execute("DROP TABLE IF EXISTS startup_contract");
        execute("CREATE TABLE startup_contract "
                + "(id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL) ENGINE=InnoDB");
    }

    @Test
    void specificFilePositionReplaysCommittedRowsThatPrecedeDebeziumBootstrap() throws Exception {
        String[] start = currentPosition();
        execute("INSERT INTO startup_contract (id,name) VALUES (1,'before-engine')");
        var options = StartupOptions.specificOffset(start[0], Long.parseLong(start[1]));
        try (var reader = splitReader(options)) {
            reader.addSplits(List.of(new MySqlBinlogSplit(null, new byte[0])));
            try {
                assertThat(collectIds(reader, Set.of(1L), Duration.ofSeconds(90))).contains(1L);
            } catch (Exception failure) {
                byte[] history = reader.snapshotHistory();
                var splitField = MySqlSplitReader.class.getDeclaredField("binlogSplit");
                splitField.setAccessible(true);
                var runtimeSplit = (MySqlBinlogSplit) splitField.get(reader);
                var selected = runtimeSplit.offset();
                var stop = new io.debezium.relational.history.HistoryRecord(
                        selected.partition(), selected.position(), null, null, null, null, null);
                var comparator = new io.debezium.connector.mysql.history.MySqlHistoryRecordComparator(null, null);
                var details = new java.util.ArrayList<String>();
                for (String entry : new String(history, java.nio.charset.StandardCharsets.UTF_8).split("\\n")) {
                    if (entry.isBlank()) continue;
                    var record = new io.debezium.relational.history.HistoryRecord(
                            io.debezium.document.DocumentReader.defaultReader().read(entry));
                    details.add("eligible=" + comparator.isAtOrBefore(record, stop)
                            + ", tableChanges=" + (record.document().getArray("tableChanges") != null
                                    ? record.document().getArray("tableChanges").size() : 0));
                }
                throw new IllegalStateException(
                        "Historical startup missing table schema; selected=" + selected
                                + ", comparisons=" + details
                                + ", history=" + new String(history, java.nio.charset.StandardCharsets.UTF_8),
                        failure);
            }
        }
    }

    @Test
    void timestampStartupReplaysEventsAtOrAfterRequestedSecond() throws Exception {
        execute("INSERT INTO startup_contract (id,name) VALUES (2,'timestamp-row')");
        long before = (System.currentTimeMillis() / 1000 - 3) * 1000;
        try (var reader = splitReader(StartupOptions.timestamp(before))) {
            reader.addSplits(List.of(new MySqlBinlogSplit(null, new byte[0])));
            assertThat(collectIds(reader, Set.of(2L), Duration.ofSeconds(90))).contains(2L);
        }
    }

    private MySqlSplitReader splitReader(StartupOptions options) {
        var db = MYSQL.metadata();
        return new MySqlSplitReader(
                MySqlSource.builder()
                        .hostname(db.host())
                        .port(db.port())
                        .username(db.username())
                        .password(db.password())
                        .serverId(25_200)
                        .topicPrefix("yak-startup-integration")
                        .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                        .table(TABLE, SCHEMA)
                        .startupOptions(options)
                        .build()
                        .config());
    }

    private static Set<Long> collectIds(MySqlSplitReader reader, Set<Long> expected, Duration deadline) throws Exception {
        Set<Long> received = new HashSet<>();
        long until = System.nanoTime() + deadline.toNanos();
        while (!received.containsAll(expected) && System.nanoTime() < until) {
            var batch = reader.fetch();
            String split;
            while ((split = batch.nextSplit()) != null) {
                MySqlFetchedRecord record;
                while ((record = batch.nextRecordFromSplit()) != null) {
                    record.rows().forEach(row -> received.add(((Number) row.row().getField(0)).longValue()));
                }
            }
            batch.recycle();
        }
        return received;
    }

    private static String[] currentPosition() throws SQLException {
        try (Connection connection = MYSQL.metadata().connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SHOW MASTER STATUS")) {
            if (!rows.next()) throw new SQLException("MySQL binlog was not enabled");
            return new String[] {rows.getString(1), Long.toString(rows.getLong(2))};
        }
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
