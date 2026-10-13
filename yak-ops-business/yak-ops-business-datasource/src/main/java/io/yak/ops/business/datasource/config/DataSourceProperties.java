package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Owns tunable Datasource service runtime settings.
 *
 * <p>Holds operational parameters, not feature flags or application database configuration.
 */
@Component
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

    /** Maximum duration of a connection test, in seconds. */
    private int connectionTestTimeoutSeconds = 5;

    public int getConnectionTestTimeoutSeconds() {
        return connectionTestTimeoutSeconds;
    }

    public void setConnectionTestTimeoutSeconds(int connectionTestTimeoutSeconds) {
        this.connectionTestTimeoutSeconds = connectionTestTimeoutSeconds;
    }
}
