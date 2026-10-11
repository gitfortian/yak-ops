package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers stateless Task plugins through Java ServiceLoader and dispatches by exact type.
 *
 * <p>Each plugin owns parsing, local validation and resource validation. The registry does not
 * implement a second validation flow or infer compatibility aliases.
 */
public final class TaskPluginRegistry {

    private final Map<String, TaskPlugin> plugins;

    public TaskPluginRegistry() {
        this(ServiceLoader.load(TaskPlugin.class, contextClassLoader()));
    }

    /** Uses an explicit provider collection for deterministic registry tests. */
    public TaskPluginRegistry(Iterable<? extends TaskPlugin> providers) {
        Map<String, TaskPlugin> discovered = new LinkedHashMap<>();
        for (TaskPlugin plugin : providers) {
            if (plugin == null || plugin.type() == null || plugin.type().isBlank()) {
                throw new TaskPluginException("Task plugin type is required");
            }
            if (discovered.putIfAbsent(plugin.type(), plugin) != null) {
                throw new TaskPluginException("Duplicate task plugin type: " + plugin.type());
            }
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    /** Lists the exact canonical Task types available in this application. */
    public List<String> types() {
        return List.copyOf(plugins.keySet());
    }

    /** Requires the exact registered Task type; unknown aliases are rejected. */
    public TaskPlugin get(String type) {
        TaskPlugin plugin = plugins.get(type);
        if (plugin == null) {
            throw new TaskPluginException("Unknown task plugin type");
        }
        return plugin;
    }

    /** Parses and locally checks a definition without querying physical resources. */
    public TaskParameters parse(String type, String content, ParameterFormat format) {
        return get(type).parse(content, format);
    }

    /** Parses once, then checks Workspace resources once, without rewriting user parameters. */
    public TaskParameters validate(String type, String content, ParameterFormat format, TaskValidationContext context) {
        TaskPlugin plugin = get(type);
        TaskParameters parameters = plugin.parse(content, format);
        plugin.validate(parameters, context);
        return parameters;
    }

    /** Exports a stored JSON snapshot in the requested representation. */
    public String format(String type, String parametersJson, ParameterFormat outputFormat) {
        TaskPlugin plugin = get(type);
        TaskParameters parameters = plugin.parse(parametersJson, ParameterFormat.JSON);
        return plugin.format(parameters, outputFormat);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            return TaskPluginRegistry.class.getClassLoader();
        }
        return loader;
    }
}
