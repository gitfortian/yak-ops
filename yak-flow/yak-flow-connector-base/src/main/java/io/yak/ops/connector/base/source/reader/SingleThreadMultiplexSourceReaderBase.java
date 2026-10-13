package io.yak.ops.connector.base.source.reader;

import io.yak.ops.connector.base.source.reader.fetcher.SingleThreadFetcherManager;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reader foundation for a single blocking SplitReader per parallel Source subtask.
 *
 * <p>Provides single-fetcher thread ownership and a bounded handover queue for JDBC/CDC
 * readers. Each parallel Source subtask owns its fetcher and queue.
 */
public abstract class SingleThreadMultiplexSourceReaderBase<E, T, SplitT extends SourceSplit, StateT>
        extends SourceReaderBase<E, T, SplitT, StateT> {

    protected SingleThreadMultiplexSourceReaderBase(
            Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory,
            RecordEmitter<E, T, StateT> emitter,
            SourceReaderContext context) {
        super(
                new SingleThreadFetcherManager<>(
                        Objects.requireNonNull(splitReaderFactory, "splitReaderFactory"),
                        Objects.requireNonNull(context, "context").getConfiguration()),
                emitter,
                context);
    }
}
