package io.yak.ops.plugin.task.datasync.sink;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;

/** JDBC target table selection and business write mode, without SQL writer configuration. */
public record JdbcSinkParameters(
        String datasourceId, String database, String schema, String table, DataSyncWriteMode writeMode)
        implements SinkParameters {
    public static final String TYPE = "JDBC";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
