package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.source.jdbc.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.mysqlcdc.MySqlCdcSourceParameters;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JdbcSourceParameters.class, name = "JDBC"),
    @JsonSubTypes.Type(value = MySqlCdcSourceParameters.class, name = "MYSQL_CDC")
})
/** Product-level Source selection, not a YakFlow Source configuration or connector factory. */
public interface SourceParameters {
    String datasourceId();

    String database();

    String schema();

    String table();

    default void validate() {
        SyncParameters.requireText(datasourceId(), "source.datasourceId", 64);
        SyncParameters.requireText(table(), "source.table", 128);
        SyncParameters.optionalText(database(), "source.database", 128);
        SyncParameters.optionalText(schema(), "source.schema", 128);
    }
}
