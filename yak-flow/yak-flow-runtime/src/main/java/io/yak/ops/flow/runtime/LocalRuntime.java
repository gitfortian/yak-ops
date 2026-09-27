package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceSplit;
import java.util.Objects;

/**
 * YakFlow 单节点执行入口，把一个 Source 与一个 Sink 连接为本地批流统一执行。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class LocalRuntime {

    private static final int DEFAULT_CHANNEL_CAPACITY = 64;

    /**
     * 启动一次本地执行。
     *
     * @param source 输入 Source
     * @param sink 输出 Sink
     * @param schema Source 与 Sink 共享的逻辑表结构
     * @param <SplitT> Source 分片类型
     * @return 已启动的本地执行
     */
    public <SplitT extends SourceSplit> LocalExecution<SplitT> start(
            Source<SplitT> source, Sink sink, YakTableSchema schema) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(schema, "schema must not be null");

        LocalExecution<SplitT> execution =
                new LocalExecution<>(source, sink, schema, DEFAULT_CHANNEL_CAPACITY);
        execution.start();
        return execution;
    }
}
