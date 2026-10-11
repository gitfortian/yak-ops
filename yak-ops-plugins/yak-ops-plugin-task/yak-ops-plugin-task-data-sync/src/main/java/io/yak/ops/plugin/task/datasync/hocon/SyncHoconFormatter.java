package io.yak.ops.plugin.task.datasync.hocon;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.sink.jdbc.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import io.yak.ops.plugin.task.datasync.source.mysqlcdc.MySqlCdcSourceParameters;
import java.util.LinkedHashMap;
import java.util.Map;

/** Formats a validated canonical pipeline as readable SeaTunnel-style HOCON. */
public final class SyncHoconFormatter {
    private static final ConfigRenderOptions OUTPUT = ConfigRenderOptions.defaults()
            .setJson(false)
            .setFormatted(true)
            .setComments(false)
            .setOriginComments(false);

    private SyncHoconFormatter() {}

    public static String format(SyncParameters parameters) {
        parameters.validate();
        Map<String, Object> root = new LinkedHashMap<>();
        String sourceType = parameters.source() instanceof MySqlCdcSourceParameters ? "MySQL-CDC" : "Jdbc";
        root.put("source", Map.of(sourceType, options(parameters.source().datasourceId(),
                parameters.source().database(), parameters.source().schema(), parameters.source().table())));
        root.put("transform", Map.of());
        if (!(parameters.sink() instanceof JdbcSinkParameters sink)) {
            throw new IllegalArgumentException("Unsupported sink");
        }
        Map<String, Object> destination = options(
                sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        destination.put("write_mode", sink.writeMode().name());
        root.put("sink", Map.of("Jdbc", destination));
        return ConfigFactory.parseMap(root).root().render(OUTPUT);
    }

    private static Map<String, Object> options(String datasourceId, String database, String schema, String table) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("datasource_id", datasourceId);
        if (database != null) values.put("database", database);
        if (schema != null) values.put("schema", schema);
        values.put("table", table);
        return values;
    }
}
