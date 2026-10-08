package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local Execution Engine 测试使用的可配置 Source，可模拟有界数据和持续无界数据。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class TestSource implements Source<TestSplit> {

    private final Boundedness boundedness;
    private final int boundedRows;
    private final long pollDelayMillis;
    private final AtomicLong completedCheckpointId = new AtomicLong();

    TestSource(Boundedness boundedness, int boundedRows, long pollDelayMillis) {
        this.boundedness = boundedness;
        this.boundedRows = boundedRows;
        this.pollDelayMillis = pollDelayMillis;
    }

    @Override
    public Boundedness boundedness() {
        return boundedness;
    }

    @Override
    public SourceSplitEnumerator<TestSplit> createEnumerator() {
        return new TestSourceSplitEnumerator();
    }

    @Override
    public SourceReader<TestSplit> createReader() {
        return new TestSourceReader(boundedness, boundedRows, pollDelayMillis, completedCheckpointId);
    }

    long completedCheckpointId() {
        return completedCheckpointId.get();
    }

    /**
     * Runtime 测试使用的 Enumerator。
     *
     * @author weifuwan
     * @since 2026-09-27
     */
    private static final class TestSourceSplitEnumerator implements SourceSplitEnumerator<TestSplit> {

        private boolean assigned;

        @Override
        public Optional<TestSplit> nextSplit() {
            if (assigned) {
                return Optional.empty();
            }
            assigned = true;
            return Optional.of(new TestSplit("test-split"));
        }

        @Override
        public boolean isFinished() {
            return assigned;
        }

        @Override
        public TestCheckpointState snapshotState(long checkpointId) {
            return new TestCheckpointState("enumerator", assigned ? 1 : 0);
        }

        @Override
        public void restore(io.yak.ops.flow.api.checkpoint.CheckpointState state) {
            assigned = ((TestCheckpointState) state).position() > 0;
        }
    }

    /**
     * Runtime 测试使用的 Reader。
     *
     * @author weifuwan
     * @since 2026-09-27
     */
    private static final class TestSourceReader implements SourceReader<TestSplit> {

        private final Boundedness boundedness;
        private final int boundedRows;
        private final long pollDelayMillis;
        private final AtomicLong completedCheckpointId;
        private int emitted;

        private TestSourceReader(
                Boundedness boundedness,
                int boundedRows,
                long pollDelayMillis,
                AtomicLong completedCheckpointId) {
            this.boundedness = boundedness;
            this.boundedRows = boundedRows;
            this.pollDelayMillis = pollDelayMillis;
            this.completedCheckpointId = completedCheckpointId;
        }

        @Override
        public void open(TestSplit split) {}

        @Override
        public List<YakRow> poll() throws InterruptedException {
            if (pollDelayMillis > 0) {
                Thread.sleep(pollDelayMillis);
            }
            if (isFinished()) {
                return List.of();
            }
            emitted++;
            return List.of(new YakRow(RowKind.INSERT, List.of((long) emitted)));
        }

        @Override
        public boolean isFinished() {
            return boundedness == Boundedness.BOUNDED && emitted >= boundedRows;
        }

        @Override
        public TestCheckpointState snapshotState(long checkpointId) {
            return new TestCheckpointState("reader", emitted);
        }

        @Override
        public void restore(io.yak.ops.flow.api.checkpoint.CheckpointState state) {
            emitted = Math.toIntExact(((TestCheckpointState) state).position());
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            completedCheckpointId.set(checkpointId);
        }

        @Override
        public void close() {}
    }
}
