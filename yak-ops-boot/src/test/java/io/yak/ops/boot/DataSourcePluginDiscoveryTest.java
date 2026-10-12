package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import org.junit.jupiter.api.Test;

/** Verifies that the application distribution exposes its JDBC datasource providers through SPI. */
class DataSourcePluginDiscoveryTest {

    @Test
    void loadsBundledJdbcProvidersAndPostgresAliases() {
        DataSourcePluginRegistry.load();

        assertEquals("MYSQL", DataSourcePluginRegistry.get("MYSQL").type());
        assertEquals("POSTGRE_SQL", DataSourcePluginRegistry.get("POSTGRE_SQL").type());
        assertEquals("POSTGRE_SQL", DataSourcePluginRegistry.get("POSTGRES").type());
        assertEquals("POSTGRE_SQL", DataSourcePluginRegistry.get("POSTGRESQL").type());
        assertEquals("ORACLE", DataSourcePluginRegistry.get("ORACLE").type());
    }
}
