package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonTypeName;

/** MySQL CDC table subscription without Debezium connections or runtime checkpoint settings. */
@JsonTypeName("MYSQL_CDC")
public record MySqlCdcSourceParameters(String datasourceId, String database, String schema, String table)
        implements SourceParameters {}
