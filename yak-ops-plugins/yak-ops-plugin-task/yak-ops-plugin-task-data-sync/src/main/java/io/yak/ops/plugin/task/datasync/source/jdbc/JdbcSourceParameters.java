package io.yak.ops.plugin.task.datasync.source.jdbc;

import io.yak.ops.plugin.task.datasync.source.SourceParameters;

/** User-facing bounded JDBC Source table reference; no JDBC URL, credentials or fetch tuning. */
public record JdbcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
