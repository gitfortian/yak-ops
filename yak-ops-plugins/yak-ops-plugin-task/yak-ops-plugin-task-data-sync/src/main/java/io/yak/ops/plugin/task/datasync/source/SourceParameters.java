package io.yak.ops.plugin.task.datasync.source;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User-selected Source identity and table path; no credentials or reader settings.
 *
 * <p>Only explicit source types are supported by this version of DATA_SYNC.
 */
public interface SourceParameters {
    /** Persistent Source type identifier used to select a registered Connector factory. */
    @JsonProperty("type")
    String type();

    String datasourceId();

    String database();

    String schema();

    String table();
}
