package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * User-selected Source identity and table path; no credentials or reader settings.
 *
 * <p>Only explicit source types are supported by this version of DATA_SYNC.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface SourceParameters {
    String datasourceId();

    String database();

    String schema();

    String table();
}
