package io.yak.ops.plugin.task.api;

import io.yak.ops.common.util.StringUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers Task plugins through ServiceLoader and resolves configuration by canonical type.
 *
 * <p>Registration is finalized at startup; duplicate types and mismatched factory/plugin types fail immediately.
 */
public final class TaskPluginRegistry {

    private final Map<String, TaskPlugin> plugins;

    /** Loads plugins from the application classpath; returns an empty registry if none are available. */
    public TaskPluginRegistry() {
        this(loadFactories());
    }

    /** Builds a registry from explicit factories for controlled assembly and isolated tests. */
    public TaskPluginRegistry(Iterable<? extends TaskPluginFactory> factories) {
        if (factories == null) {
            throw new TaskPluginException("Task plugin factories must not be null");
        }
        Map<String, TaskPlugin> discovered = new LinkedHashMap<>();
        for (TaskPluginFactory factory : factories) {
            if (factory == null) {
                throw new TaskPluginException("Task plugin factory must not be null");
            }
            String type = normalizeType(factory.type());
            TaskPlugin plugin = factory.create();
            if (plugin == null || !type.equals(normalizeType(plugin.type()))) {
                throw new TaskPluginException("Task plugin type does not match its factory: " + type);
            }
            if (discovered.putIfAbsent(type, plugin) != null) {
                throw new TaskPluginException("Duplicate task plugin type: " + type);
            }
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    /** Lists registered Task types; registration does not imply that runtime execution is integrated. */
    public List<String> types() {
        return List.copyOf(plugins.keySet());
    }

    /** Requires a registered plugin; unknown types fail rather than selecting a fallback. */
    public TaskPlugin get(String type) {
        String normalized = normalizeType(type);
        TaskPlugin plugin = plugins.get(normalized);
        if (plugin == null) {
            throw new TaskPluginException("Unknown task plugin type: " + normalized);
        }
        return plugin;
    }

    /** Parses and validates plugin configuration; any validation error prevents the business operation. */
    public TaskParameters parseParameters(String type, String parametersJson) {
        TaskParameters parameters;
        try {
            parameters = get(type).parseParameters(parametersJson);
        } catch (TaskPluginException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TaskPluginException("Invalid task plugin parameters for type: " + normalizeType(type), exception);
        }
        if (parameters == null) {
            throw new TaskPluginException("Task plugin returned null parameters: " + normalizeType(type));
        }
        parameters.validate();
        return parameters;
    }

    /**
     * Parse, normalize and validate a real Definition against Workspace-scoped read-only resources.
     * Only the host persists the result; a plugin never writes Definition tables.
     */
    public TaskParameters validateDefinition(String type, String parametersJson, TaskValidationContext context) {
        if (context == null) {
            throw new TaskPluginException("Task validation context is required");
        }
        TaskPlugin plugin = get(type);
        TaskParameters parameters = parseParameters(type, parametersJson);
        TaskParameters normalized = plugin.normalize(parameters, context);
        if (normalized == null) {
            throw new TaskPluginException("Task plugin returned null normalized parameters");
        }
        normalized.validate();
        plugin.validateDefinition(normalized, context);
        return normalized;
    }

    /** Normalizes type identifiers to uppercase underscore notation without restricting the plugin set. */
    public static String normalizeType(String type) {
        String normalized = StringUtils.trimToNull(type);
        if (normalized == null) {
            throw new TaskPluginException("Task plugin type must not be blank");
        }
        return normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static ServiceLoader<TaskPluginFactory> loadFactories() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TaskPluginRegistry.class.getClassLoader();
        }
        return ServiceLoader.load(TaskPluginFactory.class, classLoader);
    }
}
