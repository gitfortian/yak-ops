package io.yak.ops.plugin.task.datasync.source;

/** MySQL CDC table subscription without Debezium connections or runtime checkpoint settings. */
public record MySqlCdcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
