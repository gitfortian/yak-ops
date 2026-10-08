package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local Execution Engine 并行 Source Reader 测试夹具，通过 barrier 证明多个 Reader 同时进入 split。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
final class ParallelTestSource implements Source<TestSplit> {

    private final int splitCount;
    private final CyclicBarrier readerBarrier;
    private final AtomicInteger activeReaders = new AtomicInteger();
    private final AtomicInteger maxActiveReaders = new AtomicInteger();

    ParallelTestSource(int splitCount, int expectedParallelism) {
        this.splitCount = splitCount;
        this.readerBarrier = new CyclicBarrier(expectedParallelism);
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SourceSplitEnumerator<TestSplit> createEnumerator() {
        return new SourceSplitEnumerator<>() {

            private int nextSplit;

            @Override
            public Optional<TestSplit> nextSplit() {
                if (nextSplit >= splitCount) return Optional.empty();
                return Optional.of(new TestSplit("parallel-" + nextSplit++));
            }

            @Override
            public boolean isFinished() {
                return nextSplit >= splitCount;
            }

            @Override
            public TestCheckpointState snapshotState(long checkpointId) {
                return new TestCheckpointState("enumerator", nextSplit);
            }

            @Override
            public void restore(io.yak.ops.flow.api.checkpoint.CheckpointState state) {
                nextSplit = Math.toIntExact(((TestCheckpointState) state).position());
            }
        };
    }

    @Override
    public SourceReader<TestSplit> createReader() {
        return new SourceReader<>() {

            private final AtomicBoolean emitted = new AtomicBoolean();
            private boolean opened;

            @Override
            public void open(TestSplit split) throws Exception {
                opened = true;
                int active = activeReaders.incrementAndGet();
                maxActiveReaders.accumulateAndGet(active, Math::max);
                readerBarrier.await(5, TimeUnit.SECONDS);
            }

            @Override
            public List<YakRow> poll() {
                if (!emitted.compareAndSet(false, true)) return List.of();
                return List.of(new YakRow(RowKind.INSERT, List.of(1L)));
            }

            @Override
            public boolean isFinished() {
                return emitted.get();
            }

            @Override
            public TestCheckpointState snapshotState(long checkpointId) {
                return new TestCheckpointState("reader", emitted.get() ? 1 : 0);
            }

            @Override
            public void restore(io.yak.ops.flow.api.checkpoint.CheckpointState state) {
                emitted.set(((TestCheckpointState) state).position() > 0);
            }

            @Override
            public void close() {
                if (opened) {
                    activeReaders.decrementAndGet();
                    opened = false;
                }
            }
        };
    }

    int maxActiveReaders() {
        return maxActiveReaders.get();
    }
}
