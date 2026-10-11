package io.yak.ops.plugin.datasource.api.plugin;

import java.util.Map;

/**
 * Stores normalized datasource connection parameters produced by a plugin.
 */
public interface DataSourceConnection {

    /** Stable identifier of the producing plugin. */
    String type();

    /** Connection URL normalized by the plugin. */
    String jdbcUrl();

    /** JDBC driver class, or null for non-JDBC providers. */
    String driverClassName();

    /** Connection authentication username. */
    String username();

    /** Password or other sensitive connection credential. */
    String password();

    /** Default database name. */
    String database();

    /** Optional provider schema context; not all providers expose schema as a connection property. */
    String schema();

    /** Additional provider-specific connection properties. */
    Map<String, String> properties();

    /** Normalized JSON that may be stored and projected back into the edit form. */
    String normalizedJson();
}
