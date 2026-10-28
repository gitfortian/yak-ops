package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.api.connector.source.ReaderInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.metrics.Gauge;
import io.yak.ops.core.api.metrics.groups.SplitEnumeratorMetricGroup;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Asynchronous Chunk discovery and checkpoint recovery owned by the MySQL Snapshot Assigner. */
class MySqlSnapshotSplitAssignerDiscoveryTest {
    private static final TableId TABLE = new TableId(null, null, "orders");

    @Test
    void inFlightDiscoveryDoesNotAdvanceCheckpointAndRestoredAssignerReplansOnlyRemainingChunks()
            throws Exception {
        String url = database();
        createTable(url);
        DeferredContext context = new DeferredContext();
        var discovered = new ArrayList<Integer>();
        var assigner = new MySqlSnapshotSplitAssigner();
        var splitter = splitter(url);

        assertThat(schedule(assigner, splitter, context, discovered)).isTrue();
        assertThat(schedule(assigner, splitter, context, discovered)).isFalse();
        assertThat(context.actions.size()).isEqualTo(1);
        SnapshotPendingSplitsState inflight = assigner.snapshotState(1);
        assertThat(inflight.totalSnapshotSplits()).isZero();
        assertThat(inflight.nextChunkIndex()).isZero();

        context.runOne();
        assertThat(discovered).containsExactly(1);
        var first = assigner.next();
        assertThat(first.chunkRange().start()).isNull();
        assertThat(first.chunkRange().end()).isEqualTo(1000L);
        assigner.finishSnapshot(first.splitId(), offset(150));

        SnapshotPendingSplitsState saved = assigner.snapshotState(2);
        assertThat(saved.nextChunkIndex()).isEqualTo(1);
        assertThat(saved.getFinishedSplitInfos()).hasSize(1);
        assigner.close();

        var restored = new MySqlSnapshotSplitAssigner(saved);
        var recoveredContext = new DeferredContext();
        var resumed = new ArrayList<Integer>();
        assertThat(schedule(restored, splitter(url), recoveredContext, resumed)).isTrue();
        recoveredContext.runOne();
        assertThat(resumed).containsExactly(2);
        assertThat(restored.next().chunkRange().start()).isEqualTo(1000L);
        assertThat(restored.nextChunkIndex()).isEqualTo(2);
        assertThat(restored.finishedSplits()).isEqualTo(saved.getFinishedSplitInfos());
    }

    @Test
    void closingTheAssignerDiscardsAnOutstandingDiscoveryCallback() throws Exception {
        String url = database();
        createTable(url);
        var assigner = new MySqlSnapshotSplitAssigner();
        var context = new DeferredContext();
        var discovered = new ArrayList<Integer>();
        assertThat(schedule(assigner, splitter(url), context, discovered)).isTrue();
        assigner.close();
        context.runOne();
        assertThat(discovered).isEmpty();
        assertThat(assigner.snapshotState(3).totalSnapshotSplits()).isZero();
    }

    private static boolean schedule(
            MySqlSnapshotSplitAssigner assigner,
            MySqlChunkSplitter splitter,
            DeferredContext context,
            List<Integer> discovered) {
        return assigner.discoverNextAsync(
                List.of(TABLE),
                splitter,
                context,
                32,
                (split, next) -> discovered.add(next),
                (table, failure) -> {
                    throw new AssertionError("Unexpected discovery failure for " + table, failure);
                });
    }

    private static void createTable(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO orders VALUES (1),(100),(1000),(5000),(6000)");
        }
    }

    private static MySqlChunkSplitter splitter(String url) {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        return new MySqlChunkSplitter(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                Map.of(TABLE, schema),
                2);
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static String database() {
        return "jdbc:h2:mem:snapshot_assigner_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static final class DeferredContext implements SplitEnumeratorContext<MySqlSplit> {
        private final ArrayDeque<Runnable> actions = new ArrayDeque<>();

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public Map<Integer, ReaderInfo> registeredReaders() {
            return Map.of();
        }

        @Override
        public SplitEnumeratorMetricGroup metricGroup() {
            return new SplitEnumeratorMetricGroup() {
                @Override
                public <G extends Gauge<Long>> G setUnassignedSplitsGauge(G gauge) {
                    return gauge;
                }
            };
        }

        @Override
        public void assignSplits(SplitsAssignment<MySqlSplit> assignment) {}

        @Override
        public void assignSplit(MySqlSplit split, int reader) {}

        @Override
        public void signalNoMoreSplits(int reader) {}

        @Override
        public void sendEventToSourceReader(int reader, SourceEvent event) {}

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            actions.addLast(() -> {
                T value = null;
                Throwable error = null;
                try {
                    value = action.call();
                } catch (Throwable failure) {
                    error = failure;
                }
                handler.accept(value, error);
            });
        }

        @Override
        public <T> void callAsync(
                Callable<T> action,
                BiConsumer<T, Throwable> handler,
                long initialDelayMillis,
                long periodMillis) {
            throw new UnsupportedOperationException("Periodic split discovery is not required");
        }

        void runOne() {
            actions.removeFirst().run();
        }
    }
}
