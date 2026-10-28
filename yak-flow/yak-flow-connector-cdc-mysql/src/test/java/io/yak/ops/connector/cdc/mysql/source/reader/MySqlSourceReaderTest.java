package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsRequestEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * SourceReader control-plane contract tests following Flink CDC's
 * MySqlSourceReaderTest. The Flink-only metadata-update events are not
 * transplanted: YakFlow uses the finished-split reporting/ACK protocol.
 */
class MySqlSourceReaderTest {

    @Test
    void testStartRequestsSplitFromEnumeratorExactlyOncePerStart() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(requests), new TableMetrics(), false)) {
            reader.start();
            assertThat(requests.get()).isEqualTo(1);
            assertThat(reader.snapshotState(1)).isEmpty();
            reader.notifyCheckpointComplete(1);
        }
    }

    @Test
    void testBinlogOnlyReaderRejectsHybridCoordinatorEvents() throws Exception {
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger()), new TableMetrics(), false)) {
            assertThatThrownBy(() -> reader.handleSourceEvents(new FinishedSnapshotSplitsRequestEvent()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reader.handleSourceEvents(
                    new FinishedSnapshotSplitsAckEvent(List.of("chunk-0"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testSnapshotReaderRequestWithoutFinishedSplitsIsNoOp() throws Exception {
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger()), new TableMetrics(), true)) {
            reader.handleSourceEvents(new FinishedSnapshotSplitsRequestEvent());
            reader.handleSourceEvents(new FinishedSnapshotSplitsAckEvent(List.of("missing")));
            assertThat(reader.snapshotState(2)).isEmpty();
            reader.notifyCheckpointComplete(2);
        }
    }

    private static io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig config() {
        return MySqlSource.builder()
                .hostname("localhost")
                .username("reader")
                .password("secret")
                .serverId(9320)
                .topicPrefix("reader-test")
                .table(
                        new TableId("shop", null, "orders"),
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .build()
                .config();
    }

    private static SourceReaderContext context(AtomicInteger requests) {
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
            public void sendSplitRequest() {
                requests.incrementAndGet();
            }
        };
    }
}
