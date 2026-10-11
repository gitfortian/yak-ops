package io.yak.ops.plugin.task.datasync.hocon;

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
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the restricted user-facing HOCON directly into typed DATA_SYNC parameters.
 *
 * <p>Includes, substitutions, arbitrary option keys and nonempty transforms are rejected.
 * Parsing only reads the supplied text and never resolves host files or environment variables.
 */
public final class SyncHoconParser {

    private static final int MAX_LENGTH = 32_768;
    private static final int MAX_DEPTH = 16;
    private static final Set<String> ROOT_KEYS = Set.of("source", "transform", "sink");
    private static final Set<String> SOURCE_KEYS = Set.of("datasource_id", "database", "schema", "table");
    private static final Set<String> SINK_KEYS = Set.of("datasource_id", "database", "schema", "table", "write_mode");
    private static final ConfigParseOptions OPTIONS =
            ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setIncluder(new NoIncludes());

    private SyncHoconParser() {}

    /** Reads a single Source and Sink without a temporary JSON representation. */
    public static SyncParameters parse(String hocon) {
        if (hocon == null || hocon.isBlank() || hocon.length() > MAX_LENGTH) {
            throw new TaskPluginException("Invalid DATA_SYNC HOCON size");
        }
        try {
            // No substitution syntax is accepted, even when written as a quoted literal.
            if (hocon.contains("${")) {
                throw new TaskPluginException("DATA_SYNC HOCON substitutions are not supported");
            }
            Config config = ConfigFactory.parseString(hocon, OPTIONS);
            if (!config.isResolved()) {
                throw new TaskPluginException("DATA_SYNC HOCON substitutions are not supported");
            }
            ConfigObject root = config.root();
            checkDepth(root, 0);
            if (!ROOT_KEYS.containsAll(root.keySet()) || !root.containsKey("source") || !root.containsKey("sink")) {
                throw new TaskPluginException("DATA_SYNC requires only source, transform and sink");
            }
            ConfigValue transform = root.get("transform");
            if (transform != null) {
                if (transform instanceof ConfigObject object && object.isEmpty()) {
                    // An empty object is a valid placeholder for a future transformation stage.
                } else if (transform instanceof ConfigList list && list.isEmpty()) {
                    // An empty list is also accepted for compatibility with canonical JSON.
                } else {
                    throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
                }
            }
            return new SyncParameters(readSource(root.get("source")), List.of(), readSink(root.get("sink")));
        } catch (TaskPluginException exception) {
            throw exception;
        } catch (ConfigException | IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC HOCON");
        }
    }

    private static SourceParameters readSource(ConfigValue value) {
        Map.Entry<String, ConfigValue> stage = singleStage(value);
        ConfigObject options = readOptions(stage.getValue(), SOURCE_KEYS);
        String datasourceId = text(options, "datasource_id");
        String database = text(options, "database");
        String schema = text(options, "schema");
        String table = text(options, "table");
        return switch (stage.getKey()) {
            case "Jdbc" -> new JdbcSourceParameters(datasourceId, database, schema, table);
            case "MySQL-CDC" -> new MySqlCdcSourceParameters(datasourceId, database, schema, table);
            default -> throw new TaskPluginException("Unsupported DATA_SYNC Source connector");
        };
    }

    private static JdbcSinkParameters readSink(ConfigValue value) {
        Map.Entry<String, ConfigValue> stage = singleStage(value);
        if (!"Jdbc".equals(stage.getKey())) {
            throw new TaskPluginException("Unsupported DATA_SYNC Sink connector");
        }
        ConfigObject options = readOptions(stage.getValue(), SINK_KEYS);
        String mode = text(options, "write_mode");
        io.yak.ops.common.enums.datasync.DataSyncWriteMode writeMode;
        try {
            writeMode = mode == null ? null : io.yak.ops.common.enums.datasync.DataSyncWriteMode.valueOf(mode);
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Unsupported DATA_SYNC write mode");
        }
        return new JdbcSinkParameters(
                text(options, "datasource_id"),
                text(options, "database"),
                text(options, "schema"),
                text(options, "table"),
                writeMode);
    }

    private static Map.Entry<String, ConfigValue> singleStage(ConfigValue value) {
        if (!(value instanceof ConfigObject section) || section.size() != 1) {
            throw new TaskPluginException("DATA_SYNC requires exactly one Source or Sink connector");
        }
        return section.entrySet().iterator().next();
    }

    private static ConfigObject readOptions(ConfigValue value, Set<String> allowed) {
        if (!(value instanceof ConfigObject options) || !allowed.containsAll(options.keySet())) {
            throw new TaskPluginException("Unsupported DATA_SYNC connector options");
        }
        return options;
    }

    private static String text(ConfigObject object, String key) {
        ConfigValue value = object.get(key);
        if (value == null) {
            return null;
        }
        if (value.valueType() != ConfigValueType.STRING) {
            throw new TaskPluginException("DATA_SYNC connector option must be a string");
        }
        return (String) value.unwrapped();
    }

    private static void checkDepth(ConfigValue value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new TaskPluginException("DATA_SYNC HOCON is too deeply nested");
        }
        if (value instanceof ConfigObject object) {
            for (ConfigValue child : object.values()) {
                checkDepth(child, depth + 1);
            }
        } else if (value instanceof ConfigList list) {
            for (ConfigValue child : list) {
                checkDepth(child, depth + 1);
            }
        }
    }

    /** Forbids HOCON includes regardless of their resolution protocol. */
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
