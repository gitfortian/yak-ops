package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Physical MySQL CDC snapshot and backpressure settings owned by the Connector. */
public final class MySqlCdcSourceOptions {

    public static final Option<Integer> SNAPSHOT_CHUNK_SIZE =
            Options.key("data-sync.mysql-cdc.snapshot-chunk-size").intType().defaultValue(1024);

    public static final Option<Integer> QUEUE_CAPACITY =
            Options.key("data-sync.mysql-cdc.queue-capacity").intType().defaultValue(256);

    private MySqlCdcSourceOptions() {}
}
