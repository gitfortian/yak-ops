package io.yak.ops.plugin.datasource.api.enums;

/**
 * Identifies the plugin lifecycle phase where a datasource failure occurred.
 */
public enum DataSourcePluginOperation {

    /** Parsing, validation or normalization of connection settings failed. */
    PARAMETER,

    /** Driver loading, network connection or authentication failed. */
    CONNECTIVITY
}
