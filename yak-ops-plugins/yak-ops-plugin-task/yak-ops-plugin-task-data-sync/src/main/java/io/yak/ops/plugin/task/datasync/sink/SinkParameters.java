package io.yak.ops.plugin.task.datasync.sink;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.yak.ops.plugin.task.datasync.sink.jdbc.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.SyncParameters;

/** Product-level Sink selection; physical writer options remain in YakFlow connectors. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(@JsonSubTypes.Type(value = JdbcSinkParameters.class, name = "JDBC"))
public interface SinkParameters {
    String datasourceId();
    String database();
    String schema();
    String table();

    default void validate() {
        SyncParameters.requireText(datasourceId(), "sink.datasourceId", 64);
        SyncParameters.requireText(table(), "sink.table", 128);
        SyncParameters.optionalText(database(), "sink.database", 128);
        SyncParameters.optionalText(schema(), "sink.schema", 128);
    }
}
