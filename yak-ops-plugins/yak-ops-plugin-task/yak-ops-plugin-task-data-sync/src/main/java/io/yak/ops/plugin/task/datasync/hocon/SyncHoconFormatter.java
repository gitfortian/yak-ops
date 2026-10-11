package io.yak.ops.plugin.task.datasync.hocon;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders typed DATA_SYNC configuration as user-facing HOCON.
 *
 * <p>Formats explicit parameters only, without resolving resource defaults or credentials.
 */
public final class SyncHoconFormatter {

    private static final ConfigRenderOptions OUTPUT = ConfigRenderOptions.defaults()
            .setJson(false)
            .setFormatted(true)
            .setComments(false)
            .setOriginComments(false);

    private SyncHoconFormatter() {}

    public static String format(SyncParameters parameters) {
        String sourceName;
        if (parameters.source() instanceof JdbcSourceParameters) {
            sourceName = "Jdbc";
        } else if (parameters.source() instanceof MySqlCdcSourceParameters) {
            sourceName = "MySQL-CDC";
        } else {
            throw new TaskPluginException("Unsupported DATA_SYNC Source connector");
        }
        if (!(parameters.sink() instanceof JdbcSinkParameters sink)) {
            throw new TaskPluginException("Unsupported DATA_SYNC Sink connector");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put(
                "source",
                Map.of(
                        sourceName,
                        tableOptions(
                                parameters.source().datasourceId(),
                                parameters.source().database(),
                                parameters.source().schema(),
                                parameters.source().table())));
        root.put("transform", Map.of());
        Map<String, Object> target = tableOptions(sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        target.put("write_mode", sink.writeMode().name());
        root.put("sink", Map.of("Jdbc", target));
        return ConfigFactory.parseMap(root).root().render(OUTPUT);
    }

    private static Map<String, Object> tableOptions(
            String datasourceId, String database, String schema, String table) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasource_id", datasourceId);
        if (database != null) {
            result.put("database", database);
        }
        if (schema != null) {
            result.put("schema", schema);
        }
        result.put("table", table);
        return result;
    }
}
