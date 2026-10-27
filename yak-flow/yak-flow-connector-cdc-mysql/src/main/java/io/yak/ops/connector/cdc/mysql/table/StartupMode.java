package io.yak.ops.connector.cdc.mysql.table;

/** Startup modes of a MySQL CDC source, following Flink CDC's six-mode contract. */
public enum StartupMode {
    INITIAL,
    EARLIEST_OFFSET,
    LATEST_OFFSET,
    SPECIFIC_OFFSETS,
    TIMESTAMP,
    SNAPSHOT
}
