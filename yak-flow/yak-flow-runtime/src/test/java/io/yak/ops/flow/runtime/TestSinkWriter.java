package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.sink.SinkWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestSink 对应的 Writer。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class TestSinkWriter implements SinkWriter {

    private final CopyOnWriteArrayList<YakRow> rows;
    private final AtomicInteger flushCount;
    private final AtomicInteger lastFlushRowCount;

    TestSinkWriter(
            CopyOnWriteArrayList<YakRow> rows,
            AtomicInteger flushCount,
            AtomicInteger lastFlushRowCount) {
        this.rows = rows;
        this.flushCount = flushCount;
        this.lastFlushRowCount = lastFlushRowCount;
    }

    @Override
    public void write(List<YakRow> rows) {
        this.rows.addAll(rows);
    }

    @Override
    public void flush() {
        lastFlushRowCount.set(rows.size());
        flushCount.incrementAndGet();
    }

    @Override
    public void close() {}
}
