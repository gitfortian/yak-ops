package io.yak.ops.business.datasource.plugin;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import java.sql.Connection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies provider identity, aliases and registration conflicts. */
class DataSourcePluginRegistryTest {

    @Test
    void resolvesAliasesToTheirRegisteredProvider() {
        DataSourcePlugin provider = provider("POSTGRE_SQL", Set.of("POSTGRES", "POSTGRESQL"));
        DataSourcePluginRegistry registry = new DataSourcePluginRegistry(List.of(provider));

        assertSame(provider, registry.get("postgres"));
        assertSame(provider, registry.get("POSTGRE_SQL"));
        assertThrows(NoSuchElementException.class, () -> registry.get("MYSQL"));
    }

    @Test
    void rejectsDuplicateAliases() {
        assertThrows(
                IllegalStateException.class,
                () -> new DataSourcePluginRegistry(List.of(
                        provider("MYSQL", Set.of("DATABASE")),
                        provider("ORACLE", Set.of("DATABASE")))));
    }

    private DataSourcePlugin provider(String type, Set<String> aliases) {
        return new DataSourcePlugin() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public DataSourcePluginDescriptor descriptor() {
                return new DataSourcePluginDescriptor(
                        type, aliases, DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
            }

            @Override
            public DataSourceConnection parseConnection(String json) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection openConnection(DataSourceConnection connection, int timeoutSeconds) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
