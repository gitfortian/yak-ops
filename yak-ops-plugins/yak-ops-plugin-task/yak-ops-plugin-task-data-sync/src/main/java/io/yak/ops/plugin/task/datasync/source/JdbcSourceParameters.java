package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonTypeName;

/** Bounded JDBC table selection; reader fetch and split sizes remain Connector options. */
@JsonTypeName("JDBC")
public record JdbcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
