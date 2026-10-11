package io.yak.ops.plugin.datasource.api.exception;

import io.yak.ops.plugin.datasource.api.enums.DataSourcePluginOperation;

/**
 * Reports plugin parameter, connectivity or Catalog metadata failures.
 */
public class DataSourcePluginException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Plugin lifecycle operation in which the failure occurred. */
    private final DataSourcePluginOperation operation;

    public DataSourcePluginException(DataSourcePluginOperation operation, String message) {
        super(message);
        this.operation = operation;
    }

    public DataSourcePluginException(DataSourcePluginOperation operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    /** @return the plugin lifecycle operation in which the failure occurred */
    public DataSourcePluginOperation getOperation() {
        return operation;
    }
}
