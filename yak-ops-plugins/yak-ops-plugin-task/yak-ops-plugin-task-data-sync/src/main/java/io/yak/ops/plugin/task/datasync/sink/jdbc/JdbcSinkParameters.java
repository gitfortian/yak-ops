package io.yak.ops.plugin.task.datasync.sink.jdbc;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;

/** Destination JDBC table reference and business write semantics; not a JDBC writer config. */
public record JdbcSinkParameters(
        String datasourceId, String database, String schema, String table, DataSyncWriteMode writeMode)
        implements SinkParameters {}
