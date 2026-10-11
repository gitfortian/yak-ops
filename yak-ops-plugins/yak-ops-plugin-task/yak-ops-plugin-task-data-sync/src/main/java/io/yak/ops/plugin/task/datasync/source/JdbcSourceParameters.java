package io.yak.ops.plugin.task.datasync.source;

/** Bounded JDBC table selection; reader fetch and split sizes remain Connector options. */
public record JdbcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
