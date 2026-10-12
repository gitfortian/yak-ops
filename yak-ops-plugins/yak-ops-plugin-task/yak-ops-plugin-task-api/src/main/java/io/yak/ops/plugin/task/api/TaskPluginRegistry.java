package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.SpiRegistry;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Resolves exact Task plugin types through the shared SPI registration mechanism.
 *
 * <p>Plugins may be discovered from the classpath or supplied as constructed instances.
 * Parameter parsing and resource validation remain the responsibility of each plugin.
 */
public final class TaskPluginRegistry {

    private final SpiRegistry<TaskPlugin> plugins;

    /** Discovers Task plugins using their Java SPI registrations. */
    public TaskPluginRegistry() {
        try {
            plugins = SpiRegistry.load(TaskPlugin.class, TaskPluginRegistry::identifiers);
        } catch (IllegalStateException exception) {
            throw new TaskPluginException(exception.getMessage(), exception);
        }
    }

    /**
     * Registers explicitly constructed Task plugins.
     *
     * @param providers plugin instances, including plugins constructed with runtime dependencies
     */
    public TaskPluginRegistry(Iterable<? extends TaskPlugin> providers) {
        try {
            plugins = SpiRegistry.of(providers, TaskPluginRegistry::identifiers);
        } catch (IllegalStateException exception) {
            throw new TaskPluginException(exception.getMessage(), exception);
        }
    }

    /**
     * Lists the exact registered Task types.
     *
     * @return registered Task type identifiers
     */
    public List<String> types() {
        return List.copyOf(plugins.types());
    }

    /**
     * Returns the plugin registered for the exact Task type.
     *
     * @param type registered Task type
     * @return matching Task plugin
     * @throws TaskPluginException if the type is not registered
     */
    public TaskPlugin get(String type) {
        try {
            return plugins.get(type);
        } catch (NoSuchElementException exception) {
            throw new TaskPluginException("Unknown task plugin type", exception);
        }
    }

    private static List<String> identifiers(TaskPlugin plugin) {
        String type = plugin.type();
        if (type == null || type.isBlank()) {
            throw new TaskPluginException("Task plugin type is required");
        }
        return List.of(type);
    }
}
