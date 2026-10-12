package io.yak.ops.plugin.datasource.api.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

/**
 * Discovers datasource providers through Java SPI and resolves registered types and aliases.
 *
 * <p>The immutable registry is initialized once per class loader. Duplicate names and incompatible
 * provider descriptors fail registration before they can be used by the application.
 */
public final class DataSourcePluginRegistry {

    private static final Map<String, DataSourcePlugin> PLUGINS =
            registerProviders(ServiceLoader.load(DataSourcePlugin.class, contextClassLoader()));

    private DataSourcePluginRegistry() {}

    /** Ensures that datasource providers are discovered and validated during application startup. */
    public static void load() {
        if (PLUGINS.isEmpty()) {
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
        String key = DataSourcePluginDescriptor.normalizeType(type);
        DataSourcePlugin plugin = PLUGINS.get(key);
        if (plugin == null) {
            throw new NoSuchElementException("Datasource plugin not registered: " + key);
        }
        return plugin;
    }

    /** Registers providers without modifying the application-wide registry. */
    static Map<String, DataSourcePlugin> registerProviders(Iterable<? extends DataSourcePlugin> providers) {
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
        return Collections.unmodifiableMap(discovered);
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
