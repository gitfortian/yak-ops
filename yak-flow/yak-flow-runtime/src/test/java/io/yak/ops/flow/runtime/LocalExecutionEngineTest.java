package io.yak.ops.flow.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.source.Boundedness;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LocalExecutionEngineTest {

    private static final YakTableSchema SCHEMA =
            new YakTableSchema(
                    List.of(new YakColumn("id", YakTypes.BIGINT, false, null)),
                    List.of("id"));

    @Test
    void shouldRunBoundedSourceToCompletion() throws Exception {
        TestSource source = new TestSource(Boundedness.BOUNDED, 5, 0);
        TestSink sink = new TestSink();

        LocalExecution<TestSplit> execution = new LocalExecutionEngine().start(source, sink, SCHEMA);

        assertEquals(ExecutionStatus.SUCCEEDED, execution.await(Duration.ofSeconds(5)));
        assertEquals(5, sink.rows().size());
        assertEquals(new ExecutionMetrics(5, 5), execution.metrics());
        assertTrue(sink.rows().stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
        assertEquals(1, sink.flushCount());
        assertFalse(execution.failure().isPresent());
    }

    @Test
    void shouldRunBoundedSplitsWithParallelReaders() throws Exception {
        ParallelTestSource source = new ParallelTestSource(4, 4);
        TestSink sink = new TestSink();

        LocalExecution<TestSplit> execution = new LocalExecutionEngine().start(source, sink, SCHEMA, 4);

        assertEquals(ExecutionStatus.SUCCEEDED, execution.await(Duration.ofSeconds(5)));
        assertEquals(4, sink.rows().size());
        assertEquals(new ExecutionMetrics(4, 4), execution.metrics());
        assertTrue(source.maxActiveReaders() >= 4);
        assertEquals(1, sink.flushCount());
        assertFalse(execution.failure().isPresent());
    }

    @Test
    void shouldRejectParallelReadersForContinuousSource() {
        TestSource source = new TestSource(Boundedness.CONTINUOUS_UNBOUNDED, Integer.MAX_VALUE, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalExecutionEngine().start(source, new TestSink(), SCHEMA, 2));
    }

    @Test
    void shouldCheckpointAndCancelContinuousSource() throws Exception {
        TestSource source = new TestSource(Boundedness.CONTINUOUS_UNBOUNDED, Integer.MAX_VALUE, 1);
        TestSink sink = new TestSink();
        LocalExecution<TestSplit> execution = new LocalExecutionEngine().start(source, sink, SCHEMA);

        waitForRows(sink, 5, Duration.ofSeconds(5));
        LocalCheckpoint checkpoint = execution.checkpoint().get(5, TimeUnit.SECONDS);

        TestCheckpointState readerState = (TestCheckpointState) checkpoint.readerStateOptional().orElseThrow();
        assertEquals("test-split", checkpoint.splitIdOptional().orElseThrow());
        assertEquals("reader", readerState.owner());
        assertEquals(readerState.position(), sink.lastFlushRowCount());
        assertEquals(checkpoint, execution.latestCheckpoint().orElseThrow());
        assertEquals(checkpoint.checkpointId(), source.completedCheckpointId());

        execution.cancel();

        assertEquals(ExecutionStatus.CANCELED, execution.await(Duration.ofSeconds(5)));
        assertTrue(execution.metrics().readRows() >= 5);
        assertTrue(execution.metrics().writeRows() >= 5);
        assertTrue(execution.metrics().writeRows() <= execution.metrics().readRows());
        assertTrue(sink.flushCount() >= 1);
        assertFalse(execution.failure().isPresent());
    }

    private static void waitForRows(TestSink sink, int minimumRows, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (sink.rows().size() < minimumRows && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(sink.rows().size() >= minimumRows);
    }
}
