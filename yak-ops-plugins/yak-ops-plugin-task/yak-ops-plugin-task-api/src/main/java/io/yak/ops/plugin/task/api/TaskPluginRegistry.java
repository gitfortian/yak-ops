package io.yak.ops.plugin.task.api;

import io.yak.ops.common.util.StringUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/** Discovers Task plugins and delegates configuration interpretation to their owning plugin. */
public final class TaskPluginRegistry {
    private final Map<String, TaskPlugin> plugins;

    public TaskPluginRegistry() {
        this(loadFactories());
    }

    public TaskPluginRegistry(Iterable<? extends TaskPluginFactory> factories) {
        if (factories == null) throw new TaskPluginException("Task plugin factories must not be null");
        Map<String, TaskPlugin> discovered = new LinkedHashMap<>();
        for (TaskPluginFactory factory : factories) {
            if (factory == null) throw new TaskPluginException("Task plugin factory must not be null");
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

    public List<String> types() {
        return List.copyOf(plugins.keySet());
    }

    public TaskPlugin get(String type) {
        String normalized = normalizeType(type);
        TaskPlugin plugin = plugins.get(normalized);
        if (plugin == null) throw new TaskPluginException("Unknown task plugin type: " + normalized);
        return plugin;
    }

    public TaskParameters parseParameters(String type, String parametersJson) {
        return parseParameters(type, parametersJson, "JSON");
    }

    public TaskParameters parseParameters(String type, String parameters, String format) {
        TaskParameters result;
        try {
            result = get(type).parseParameters(parameters, format);
        } catch (TaskPluginException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TaskPluginException("Invalid task plugin parameters for type: " + normalizeType(type), exception);
        }
        if (result == null) throw new TaskPluginException("Task plugin returned null parameters");
        result.validate();
        return result;
    }

    public TaskParameters validateDefinition(String type, String parametersJson, TaskValidationContext context) {
        return validateDefinition(type, parametersJson, context, "JSON");
    }

    public TaskParameters validateDefinition(
            String type, String parameters, TaskValidationContext context, String format) {
        if (context == null) throw new TaskPluginException("Task validation context is required");
        TaskPlugin plugin = get(type);
        TaskParameters parsed = parseParameters(type, parameters, format);
        TaskParameters normalized = plugin.normalize(parsed, context);
        if (normalized == null) throw new TaskPluginException("Task plugin returned null normalized parameters");
        normalized.validate();
        plugin.validateDefinition(normalized, context);
        return normalized;
    }

    public static String normalizeType(String type) {
        String text = StringUtils.trimToNull(type);
        if (text == null) throw new TaskPluginException("Task plugin type must not be blank");
        return text.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static ServiceLoader<TaskPluginFactory> loadFactories() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) classLoader = TaskPluginRegistry.class.getClassLoader();
        return ServiceLoader.load(TaskPluginFactory.class, classLoader);
    }
}
