package io.yak.ops.plugin.task.datasync.sink;

import com.fasterxml.jackson.annotation.JsonProperty;

/** User-selected Sink identity and table path; physical write settings remain in YakFlow. */
public interface SinkParameters {
    /** Persistent Sink type identifier used to select a registered Connector factory. */
    @JsonProperty("type")
    String type();

    String datasourceId();

    String database();

    String schema();

    String table();
}
