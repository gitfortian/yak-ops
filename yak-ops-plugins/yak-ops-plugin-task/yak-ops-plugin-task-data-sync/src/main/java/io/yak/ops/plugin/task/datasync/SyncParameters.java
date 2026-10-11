package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.sink.jdbc.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import io.yak.ops.plugin.task.datasync.source.jdbc.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.mysqlcdc.MySqlCdcSourceParameters;
import java.util.List;

/**
 * Immutable product-level data sync pipeline. Source type determines boundedness.
 *
 * <p>All performance, checkpoint, retry and credential options belong to the future Task
 * execution planner, YakFlow Runtime, or the physical Connector, never to this definition.
 */
public record SyncParameters(SourceParameters source, List<JsonNode> transform, SinkParameters sink)
        implements TaskParameters {

    public SyncParameters {
        transform = transform == null ? List.of() : List.copyOf(transform);
    }

    public DataSyncType syncType() {
        return source instanceof MySqlCdcSourceParameters ? DataSyncType.REALTIME : DataSyncType.OFFLINE;
    }

    @Override
    public void validate() {
        if (source == null || sink == null) {
            throw new TaskPluginException("DATA_SYNC requires exactly one source and one sink");
        }
        if (!(source instanceof JdbcSourceParameters || source instanceof MySqlCdcSourceParameters)) {
            throw new TaskPluginException("Unsupported DATA_SYNC source");
        }
        if (!(sink instanceof JdbcSinkParameters jdbcSink)) {
            throw new TaskPluginException("Unsupported DATA_SYNC sink");
        }
        if (!transform.isEmpty()) {
            throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
        }
        source.validate();
        sink.validate();
        if (jdbcSink.writeMode() == null || jdbcSink.writeMode() == DataSyncWriteMode.OVERWRITE) {
            throw new TaskPluginException("DATA_SYNC supports APPEND or UPSERT only");
        }
        if (syncType() == DataSyncType.REALTIME && jdbcSink.writeMode() != DataSyncWriteMode.UPSERT) {
            throw new TaskPluginException("MySQL CDC requires an UPSERT sink to preserve changes");
        }
    }

    public static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new TaskPluginException(field + " is required");
        optionalText(value, field, maxLength);
    }

    public static void optionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new TaskPluginException(field + " exceeds " + maxLength + " characters");
        }
    }
}
