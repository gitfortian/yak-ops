package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Bounded JDBC table selection; reader fetch and split sizes remain Connector options. */
public record JdbcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {
    public static final String TYPE = "JDBC";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
