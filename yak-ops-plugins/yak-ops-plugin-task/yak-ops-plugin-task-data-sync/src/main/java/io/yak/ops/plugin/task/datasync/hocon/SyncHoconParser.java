package io.yak.ops.plugin.task.datasync.hocon;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderClasspath;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigIncluderURL;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskPluginException;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/** Parses the restricted user-facing Source / Transform / Sink HOCON format in memory only. */
public final class SyncHoconParser {
    private static final int MAX_LENGTH = 32_768;
    private static final int MAX_DEPTH = 16;
    private static final Set<String> ROOT_KEYS = Set.of("source", "transform", "sink");
    private static final ConfigParseOptions OPTIONS =
            ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setIncluder(new NoIncludes());

    private SyncHoconParser() {}

    /** Returns the canonical pipeline JSON shape; does not load files or resolve variables. */
    public static String parse(String hocon) {
        if (hocon == null || hocon.isBlank() || hocon.length() > MAX_LENGTH) {
            throw new TaskPluginException("Invalid DATA_SYNC HOCON size");
        }
        try {
            Config config = ConfigFactory.parseString(hocon, OPTIONS);
            if (!config.isResolved()) {
                throw new TaskPluginException("DATA_SYNC HOCON substitutions are not supported");
            }
            ConfigObject root = config.root();
            limitDepth(root, 0);
            if (!ROOT_KEYS.containsAll(root.keySet()) || !root.containsKey("source") || !root.containsKey("sink")) {
                throw new TaskPluginException("DATA_SYNC requires only source, transform and sink");
            }
            ConfigValue transform = root.get("transform");
            if (transform != null
                    && !((transform instanceof ConfigObject object && object.isEmpty())
                            || (transform instanceof ConfigList list && list.isEmpty()))) {
                throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
            }
            ObjectNode canonical = JSONUtils.createObjectNode();
            canonical.set("source", stage(root.get("source"), true));
            canonical.set("transform", JSONUtils.readTree("[]"));
            canonical.set("sink", stage(root.get("sink"), false));
            return JSONUtils.toJson(canonical);
        } catch (TaskPluginException error) {
            throw error;
        } catch (ConfigException | IllegalArgumentException error) {
            throw new TaskPluginException("Invalid DATA_SYNC HOCON");
        }
    }

    private static ObjectNode stage(ConfigValue value, boolean source) {
        if (!(value instanceof ConfigObject section) || section.size() != 1) {
            throw new TaskPluginException("DATA_SYNC requires exactly one source and sink connector");
        }
        Map.Entry<String, ConfigValue> entry = section.entrySet().iterator().next();
        String type = entry.getKey().trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if (!(source ? Set.of("JDBC", "MYSQL_CDC") : Set.of("JDBC")).contains(type)) {
            throw new TaskPluginException("Unsupported DATA_SYNC connector type");
        }
        if (!(entry.getValue() instanceof ConfigObject options)) {
            throw new TaskPluginException("DATA_SYNC connector options must be an object");
        }
        ObjectNode json = JSONUtils.createObjectNode();
        json.put("type", type);
        for (var option : options.entrySet()) {
            String key =
                    switch (option.getKey()) {
                        case "datasource_id" -> "datasourceId";
                        case "database", "schema", "table" -> option.getKey();
                        case "write_mode" -> "writeMode";
                        default -> throw new TaskPluginException("Unsupported DATA_SYNC connector option");
                    };
            if (source && key.equals("writeMode")) {
                throw new TaskPluginException("Write mode belongs to the sink");
            }
            if (option.getValue().valueType() != ConfigValueType.STRING) {
                throw new TaskPluginException("DATA_SYNC connector option must be a string");
            }
            json.put(key, (String) option.getValue().unwrapped());
        }
        return json;
    }

    private static void limitDepth(ConfigValue value, int depth) {
        if (depth > MAX_DEPTH) throw new TaskPluginException("DATA_SYNC HOCON is too deeply nested");
        if (value instanceof ConfigObject object) {
            for (ConfigValue child : object.values()) limitDepth(child, depth + 1);
        } else if (value instanceof ConfigList list) {
            for (ConfigValue child : list) limitDepth(child, depth + 1);
        }
    }

    /** Explicitly blocks heuristic, file, URL and classpath includes, including required(...). */
    private static final class NoIncludes
            implements ConfigIncluder, ConfigIncluderFile, ConfigIncluderURL, ConfigIncluderClasspath {
        @Override
        public ConfigIncluder withFallback(ConfigIncluder fallback) {
            return this;
        }

        @Override
        public ConfigObject include(ConfigIncludeContext context, String name) {
            throw new TaskPluginException("DATA_SYNC HOCON includes are not supported");
        }

        @Override
        public ConfigObject includeFile(ConfigIncludeContext context, File file) {
            throw new TaskPluginException("DATA_SYNC HOCON includes are not supported");
        }

        @Override
        public ConfigObject includeURL(ConfigIncludeContext context, URL url) {
            throw new TaskPluginException("DATA_SYNC HOCON includes are not supported");
        }

        @Override
        public ConfigObject includeResources(ConfigIncludeContext context, String resource) {
            throw new TaskPluginException("DATA_SYNC HOCON includes are not supported");
        }
    }
}
