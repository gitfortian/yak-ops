package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.spi.SpiRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Discovers datasource plugins through Java SPI and resolves registered types and aliases.
 *
 * <p>The registry is immutable and initialized once per class loader. Provider descriptors are
 * validated during registration, before datasource connections can be opened.
 */
public final class DataSourcePluginRegistry {

    private static final SpiRegistry<DataSourcePlugin> PLUGINS =
            SpiRegistry.load(DataSourcePlugin.class, DataSourcePluginRegistry::identifiers);

    private DataSourcePluginRegistry() {}

    /** Ensures datasource providers are discovered and validated during application startup. */
    public static void load() {
        if (PLUGINS.types().isEmpty()) {
            throw new IllegalStateException("No datasource plugins are registered");
        }
    }

    /**
     * Returns the provider registered under its type or alias.
     *
     * @param type canonical provider type or alias
     * @return registered datasource provider
     * @throws NoSuchElementException if the type or alias is not registered
     */
    public static DataSourcePlugin get(String type) {
        String key = DataSourcePluginDescriptor.canonicalTypeName(type);
        return PLUGINS.get(key);
    }

    /** Registers explicit providers for deterministic tests without changing the global registry. */
    static SpiRegistry<DataSourcePlugin> registerProviders(Iterable<? extends DataSourcePlugin> providers) {
        return SpiRegistry.of(providers, DataSourcePluginRegistry::identifiers);
    }

    private static Collection<String> identifiers(DataSourcePlugin plugin) {
        DataSourcePluginDescriptor descriptor = plugin.descriptor();
        if (descriptor == null) {
            throw new IllegalStateException("Datasource plugin descriptor must not be null");
        }
        String type = DataSourcePluginDescriptor.canonicalTypeName(plugin.type());
        if (!descriptor.type().equals(type)) {
            throw new IllegalStateException("Datasource plugin type does not match its descriptor: " + type);
        }
        if (!DataSourcePluginDescriptor.CURRENT_API_VERSION.equals(descriptor.apiVersion())) {
            throw new IllegalStateException("Unsupported datasource plugin API version: " + descriptor.apiVersion());
        }
        Collection<String> identifiers = new ArrayList<>();
        identifiers.add(type);
        identifiers.addAll(descriptor.aliases());
        return identifiers;
    }
}
