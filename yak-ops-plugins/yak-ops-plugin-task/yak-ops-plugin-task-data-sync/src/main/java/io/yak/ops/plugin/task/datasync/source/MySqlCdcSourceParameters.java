package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonProperty;

/** MySQL CDC table subscription without Debezium connections or runtime checkpoint settings. */
public record MySqlCdcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {
    public static final String TYPE = "MYSQL_CDC";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
