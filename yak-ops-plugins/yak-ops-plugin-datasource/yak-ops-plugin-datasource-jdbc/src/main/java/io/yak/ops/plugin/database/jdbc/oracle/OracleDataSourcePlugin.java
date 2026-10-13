package io.yak.ops.plugin.database.jdbc.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auto.service.AutoService;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accepts native Oracle JDBC URLs and builds service-name URLs from structured host,
 * port and database settings.
 */
@AutoService(DataSourcePlugin.class)
public final class OracleDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.of(
            "oracle.net.connect_timeout", "oracle.net.CONNECT_TIMEOUT",
            "oracle.jdbc.readtimeout", "oracle.jdbc.ReadTimeout",
            "defaultrowprefetch", "defaultRowPrefetch",
            "oracle.jdbc.fanenabled", "oracle.jdbc.fanEnabled");

    private static final Option<Integer> CONNECT_TIMEOUT =
            Options.key("properties.oracle.net.CONNECT_TIMEOUT").intType().noDefaultValue();
    private static final Option<Integer> READ_TIMEOUT =
            Options.key("properties.oracle.jdbc.ReadTimeout").intType().noDefaultValue();
    private static final Option<Integer> ROW_PREFETCH =
            Options.key("properties.defaultRowPrefetch").intType().noDefaultValue();
    private static final Option<Boolean> FAN_ENABLED =
            Options.key("properties.oracle.jdbc.fanEnabled").booleanType().noDefaultValue();

    @Override
    protected OptionRule providerOptionRule() {
        return OptionRule.builder()
                .optional(FAN_ENABLED)
                .optional(CONNECT_TIMEOUT, Conditions.greaterOrEqual(CONNECT_TIMEOUT, 0))
                .optional(READ_TIMEOUT, Conditions.greaterOrEqual(READ_TIMEOUT, 0))
                .optional(ROW_PREFETCH, Conditions.greaterThan(ROW_PREFETCH, 0))
                .build();
    }

    @Override
    public String type() {
        return "ORACLE";
    }

    @Override
    protected int defaultPort() {
        return 1521;
    }

    @Override
    protected String defaultDriverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
    }

    @Override
    public List<String> connectionPropertyKeys() {
        return List.of();
    }

    @Override
    protected Set<String> knownConnectionPropertyKeys() {
        return Set.copyOf(PROPERTY_KEYS.values());
    }

    @Override
    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        String enabled = normalized.get("oracle.jdbc.fanEnabled");
        if (enabled != null) {
            normalized.put("oracle.jdbc.fanEnabled", enabled.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return normalized;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oracle:");
    }
}
