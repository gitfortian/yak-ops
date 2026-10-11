package io.yak.ops.plugin.task.datasync.sink;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** User-selected Sink identity and table path; physical write settings remain in YakFlow. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(@JsonSubTypes.Type(value = JdbcSinkParameters.class, name = "JDBC"))
public interface SinkParameters {
    String datasourceId();

    String database();

    String schema();

    String table();
}
