package io.yak.ops.connector.base.source.reader;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Bounded handover and shutdown settings shared by asynchronous source readers. */
public final class SourceReaderOptions {

    public static final Option<Integer> ELEMENT_QUEUE_CAPACITY =
            Options.key("source.reader.element.queue.capacity").intType().defaultValue(2);

    public static final Option<Long> CLOSE_TIMEOUT_MILLIS =
            Options.key("source.reader.close.timeout").longType().defaultValue(30_000L);

    private SourceReaderOptions() {}
}
