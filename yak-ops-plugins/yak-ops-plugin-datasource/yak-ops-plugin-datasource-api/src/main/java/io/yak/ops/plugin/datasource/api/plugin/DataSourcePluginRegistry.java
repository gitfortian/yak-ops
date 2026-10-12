package io.yak.ops.plugin.datasource.api.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

/**
 * Discovers datasource plugins and resolves registered provider types and aliases.
 */
public final class DataSourcePluginRegistry {

    private final Map<String, DataSourcePlugin> plugins;

    public DataSourcePluginRegistry() {
        this(ServiceLoader.load(DataSourcePlugin.class, contextClassLoader()));
    }

    /** Registers the supplied providers and rejects duplicate types or aliases. */
    public DataSourcePluginRegistry(Iterable<? extends DataSourcePlugin> providers) {
        Map<String, DataSourcePlugin> discovered = new LinkedHashMap<>();
        for (DataSourcePlugin plugin : providers) {
            if (plugin == null) {
                throw new IllegalStateException("Datasource plugin must not be null");
            }
            DataSourcePluginDescriptor descriptor = plugin.descriptor();
            if (descriptor == null) {
                throw new IllegalStateException("Datasource plugin descriptor must not be null");
            }
            String type = DataSourcePluginDescriptor.normalizeType(plugin.type());
            if (!descriptor.type().equals(type)) {
                throw new IllegalStateException("Datasource plugin type does not match its descriptor: " + type);
            }
            if (!DataSourcePluginDescriptor.CURRENT_API_VERSION.equals(descriptor.apiVersion())) {
                throw new IllegalStateException(
                        "Unsupported datasource plugin API version: " + descriptor.apiVersion());
            }
            register(discovered, type, plugin);
            for (String alias : descriptor.aliases()) {
                register(discovered, alias, plugin);
            }
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    /** Returns the provider registered for the given canonical type or alias. */
    public DataSourcePlugin get(String type) {
        String key = DataSourcePluginDescriptor.normalizeType(type);
        DataSourcePlugin plugin = plugins.get(key);
        if (plugin == null) {
            throw new NoSuchElementException("Datasource plugin not registered: " + key);
        }
        return plugin;
    }

    private static void register(Map<String, DataSourcePlugin> providers, String type, DataSourcePlugin plugin) {
        DataSourcePlugin registered = providers.putIfAbsent(type, plugin);
        if (registered != null && registered != plugin) {
            throw new IllegalStateException("Duplicate datasource plugin type or alias: " + type);
        }
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? DataSourcePluginRegistry.class.getClassLoader() : loader;
    }
}
