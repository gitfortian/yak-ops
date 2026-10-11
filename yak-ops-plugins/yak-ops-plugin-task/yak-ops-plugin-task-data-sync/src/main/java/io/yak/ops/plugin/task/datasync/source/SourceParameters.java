package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JdbcSourceParameters.class, name = "JDBC"),
    @JsonSubTypes.Type(value = MySqlCdcSourceParameters.class, name = "MYSQL_CDC")
})
/**
 * User-selected Source identity and table path; no credentials or reader settings.
 *
 * <p>Only explicit source types are supported by this version of DATA_SYNC.
 */
public interface SourceParameters {
    String datasourceId();

    String database();

    String schema();

    String table();
}
