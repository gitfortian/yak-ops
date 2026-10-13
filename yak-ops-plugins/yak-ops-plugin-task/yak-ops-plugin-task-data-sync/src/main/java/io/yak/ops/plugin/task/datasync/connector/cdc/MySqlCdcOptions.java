package io.yak.ops.plugin.task.datasync.connector.cdc;

import io.yak.ops.core.configuration.ConfigOption;
import io.yak.ops.core.configuration.ConfigOptions;
import io.yak.ops.core.configuration.Configuration;

/**
 * System-owned MySQL CDC snapshot and queue settings.
 *
 * <p>Configuration keys are internal to physical Connector construction and are not part of
 * the persisted DATA_SYNC Definition.
 *
 * @param snapshotChunkSize approximate maximum rows per snapshot chunk
 * @param queueCapacity maximum pending CDC records before reader backpressure
 */
public record MySqlCdcOptions(int snapshotChunkSize, int queueCapacity) {

    public static final ConfigOption<Integer> SNAPSHOT_CHUNK_SIZE = ConfigOptions.key(
                    "data-sync.mysql-cdc.snapshot-chunk-size")
            .intType()
            .defaultValue(1024);
    public static final ConfigOption<Integer> QUEUE_CAPACITY =
            ConfigOptions.key("data-sync.mysql-cdc.queue-capacity").intType().defaultValue(256);

    public MySqlCdcOptions {
        if (snapshotChunkSize < 1 || snapshotChunkSize > 100_000) {
            throw new IllegalArgumentException("MySQL CDC snapshot chunk size must be between 1 and 100000");
        }
        if (queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("MySQL CDC queue capacity must be between 1 and 10000");
        }
    }

    /** Returns Connector defaults when no system override is provided. */
    public static MySqlCdcOptions defaults() {
        return new MySqlCdcOptions(1024, 256);
    }

    /** Creates a typed configuration for Connector construction. */
    public Configuration configuration() {
        return new Configuration().set(SNAPSHOT_CHUNK_SIZE, snapshotChunkSize).set(QUEUE_CAPACITY, queueCapacity);
    }
}
