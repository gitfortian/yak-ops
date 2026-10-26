package io.yak.ops.plugin.database.jdbc.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auto.service.AutoService;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.util.Map;
import java.util.Set;

/**
 * Defines PostgreSQL provider defaults, driver, URL, connection properties and type aliases.
 *
 * <p>Schema belongs to Catalog namespaces or the optional currentSchema connection property.
 */
@AutoService(DataSourcePlugin.class)
public final class PostgreSqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.ofEntries(
            Map.entry("sslmode", "sslmode"),
            Map.entry("connecttimeout", "connectTimeout"),
            Map.entry("sockettimeout", "socketTimeout"),
            Map.entry("applicationname", "ApplicationName"),
            Map.entry("currentschema", "currentSchema"),
            Map.entry("tcpkeepalive", "tcpKeepAlive"),
            Map.entry("rewritebatchedinserts", "reWriteBatchedInserts"));

    private static final Option<String> SSL_MODE = Options.key("properties.sslmode")
            .singleChoice(
                    String.class,
                    java.util.List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full"))
            .noDefaultValue();
    private static final Option<Integer> CONNECT_TIMEOUT =
            Options.key("properties.connectTimeout").intType().noDefaultValue();
    private static final Option<Integer> SOCKET_TIMEOUT =
            Options.key("properties.socketTimeout").intType().noDefaultValue();
    private static final Option<String> APPLICATION_NAME =
            Options.key("properties.ApplicationName").stringType().noDefaultValue();
    private static final Option<String> CURRENT_SCHEMA =
            Options.key("properties.currentSchema").stringType().noDefaultValue();
    private static final Option<Boolean> TCP_KEEPALIVE =
            Options.key("properties.tcpKeepAlive").booleanType().noDefaultValue();
    private static final Option<Boolean> REWRITE_BATCHES =
            Options.key("properties.reWriteBatchedInserts").booleanType().noDefaultValue();

    @Override
    protected OptionRule providerOptionRule() {
        return OptionRule.builder()
                .optional(SSL_MODE, TCP_KEEPALIVE, REWRITE_BATCHES)
                .optional(CONNECT_TIMEOUT, Conditions.greaterOrEqual(CONNECT_TIMEOUT, 0))
                .optional(SOCKET_TIMEOUT, Conditions.greaterOrEqual(SOCKET_TIMEOUT, 0))
                .optional(APPLICATION_NAME, Conditions.notBlank(APPLICATION_NAME))
                .optional(CURRENT_SCHEMA, Conditions.notBlank(CURRENT_SCHEMA))
                .build();
    }

    @Override
    public String type() {
        return "POSTGRE_SQL";
    }

    @Override
    public String displayJdbcUrl(DataSourceConnection connection) {
        return displayJdbcUrlWithQueryProperties(connection);
    }

    @Override
    protected Set<String> aliases() {
        return Set.of("POSTGRESQL", "POSTGRES");
    }

    @Override
    protected int defaultPort() {
        return 5432;
    }

    @Override
    protected String defaultDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    protected Set<String> knownConnectionPropertyKeys() {
        return Set.copyOf(PROPERTY_KEYS.values());
    }

    @Override
    protected String resolveSchema(String schema) {
        String normalized = StringUtils.trimToNull(schema);
        if (normalized != null) {
            throw parameterError("PostgreSQL schema 不属于数据源连接字段，请通过 properties.currentSchema 配置", null);
        }
        return null;
    }

    @Override
    protected Map<String, String> prepareConnectionProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        for (String key : java.util.List.of("sslmode", "tcpKeepAlive", "reWriteBatchedInserts")) {
            String value = normalized.get(key);
            if (value != null) {
                normalized.put(key, value.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return normalized;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }
}
