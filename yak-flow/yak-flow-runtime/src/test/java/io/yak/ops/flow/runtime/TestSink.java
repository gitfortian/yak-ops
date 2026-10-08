package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.sink.SinkWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local Execution Engine 测试使用的内存 Sink，记录收到的数据和 flush 时的行数。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class TestSink implements Sink {

    private final CopyOnWriteArrayList<YakRow> rows = new CopyOnWriteArrayList<>();
    private final AtomicInteger flushCount = new AtomicInteger();
    private final AtomicInteger lastFlushRowCount = new AtomicInteger();

    @Override
    public SinkWriter createWriter(YakTableSchema schema) {
        return new TestSinkWriter(rows, flushCount, lastFlushRowCount);
    }

    List<YakRow> rows() {
        return List.copyOf(rows);
    }

    int flushCount() {
        return flushCount.get();
    }

    int lastFlushRowCount() {
        return lastFlushRowCount.get();
    }
}
