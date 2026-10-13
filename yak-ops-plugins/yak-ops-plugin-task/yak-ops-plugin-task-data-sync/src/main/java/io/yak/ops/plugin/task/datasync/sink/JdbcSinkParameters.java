package io.yak.ops.plugin.task.datasync.sink;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;

/** JDBC target table selection and business write mode, without SQL writer configuration. */
@JsonTypeName("JDBC")
public record JdbcSinkParameters(
        String datasourceId, String database, String schema, String table, DataSyncWriteMode writeMode)
        implements SinkParameters {}
