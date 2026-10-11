package io.yak.ops.plugin.task.datasync.source.mysqlcdc;

import io.yak.ops.plugin.task.datasync.source.SourceParameters;

/** User-facing continuous MySQL CDC source selection, without Debezium runtime options. */
public record MySqlCdcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
