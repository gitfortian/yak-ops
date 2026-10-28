package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.jdbc.database.internal.convert.MySqlTypeConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real MySQL BIGINT UNSIGNED bounds must never truncate to signed Java long. */
@Tag("cdc")
@Timeout(180)
class MySqlUnsignedChunkIT {

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-unsigned-chunk", true, false);

    private static final TableId TABLE = new TableId("yak_cdc_it", null, "unsigned_chunk");
    private static final BigDecimal ABOVE_SIGNED = new BigDecimal("9223372036854775808");
    private static final BigDecimal MAX_UNSIGNED = new BigDecimal("18446744073709551615");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.decimal(20, 0).copy(false)),
                    new Column("name", LogicalTypes.varchar(20).copy(false))),
            List.of("id"));

    @Test
    void chunkPlanningSnapshotAndCheckpointPreserveFullUnsignedRange() throws Exception {
        execute("DROP TABLE IF EXISTS unsigned_chunk");
        execute("CREATE TABLE unsigned_chunk "
                + "(id BIGINT UNSIGNED NOT NULL PRIMARY KEY, name VARCHAR(20) NOT NULL) ENGINE=InnoDB");
        execute("INSERT INTO unsigned_chunk (id,name) VALUES "
                + "(9223372036854775807,'signed-max'),"
                + "(9223372036854775808,'above-signed'),"
                + "(18446744073709551615,'unsigned-max')");

        assertThat(new MySqlTypeConverter().convertColumn(
                        Types.BIGINT, "BIGINT UNSIGNED", 20, 0, false, "id"))
                .isEqualTo(LogicalTypes.decimal(20, 0).copy(false));

        MySqlSourceConfig config = config();
        var splitter = new MySqlChunkSplitter(config, 2);
        List<ChunkRange> ranges = splitter.splitChunks(TABLE);
        assertThat(ranges).hasSize(2);
        assertThat(ranges.getFirst().type()).isEqualTo(LogicalTypeRoot.DECIMAL);
        assertThat(ranges.getFirst().end()).isEqualTo(ABOVE_SIGNED);
        assertThat(ranges.getLast().start()).isEqualTo(ABOVE_SIGNED);
        assertThat(ranges.getLast().end()).isNull();

        MySqlSplitSerializer serializer = MySqlSplitSerializer.INSTANCE;
        MySqlSnapshotSplit restored = (MySqlSnapshotSplit) serializer.deserialize(
                serializer.getVersion(),
                serializer.serialize(MySqlSnapshotSplit.typed("unsigned-1", ranges.getLast())));
        assertThat(restored.chunkRange().start()).isEqualTo(ABOVE_SIGNED);

        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(config, 0)) {
            var first = reader.read(MySqlSnapshotSplit.typed("unsigned-0", ranges.getFirst()));
            var last = reader.read(restored);
            assertThat(first.output().rows()).hasSize(1);
            assertThat(last.output().rows()).hasSize(2);
            assertThat(last.output().rows())
                    .extracting(row -> new BigDecimal(row.row().getField(0).toString()))
                    .containsExactlyInAnyOrder(ABOVE_SIGNED, MAX_UNSIGNED);
        }
    }

    private static MySqlSourceConfig config() {
        var database = MYSQL.metadata();
        return MySqlSource.builder()
                .hostname(database.host())
                .port(database.port())
                .username(database.username())
                .password(database.password())
                .serverId(24_900)
                .topicPrefix("yak-unsigned-chunk")
                .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                .table(TABLE, SCHEMA)
                .buildHybrid(2)
                .config();
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
