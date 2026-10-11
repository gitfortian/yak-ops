package io.yak.ops.plugin.database.jdbc.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Map;
import java.util.Set;

/**
 * Defines PostgreSQL provider defaults, driver, URL, properties and compatibility aliases.
 *
 * <p>Schema belongs to Catalog namespaces or the optional currentSchema connection property.
 */
public final class PostgreSqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.ofEntries(
            Map.entry("sslmode", "sslmode"),
            Map.entry("connecttimeout", "connectTimeout"),
            Map.entry("sockettimeout", "socketTimeout"),
            Map.entry("applicationname", "ApplicationName"),
            Map.entry("currentschema", "currentSchema"),
            Map.entry("tcpkeepalive", "tcpKeepAlive"),
            Map.entry("rewritebatchedinserts", "reWriteBatchedInserts"));

    private static final Set<String> SSL_MODES =
            Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

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
    protected String normalizeSchema(String schema) {
        String normalized = StringUtils.trimToNull(schema);
        if (normalized != null) {
            throw parameterError("PostgreSQL schema 不属于数据源连接字段，请通过 properties.currentSchema 配置", null);
        }
        return null;
    }

    @Override
    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        normalizeLowerCaseEnumProperty(normalized, "sslmode", SSL_MODES);
        normalizeBooleanProperty(normalized, "tcpKeepAlive");
        normalizeBooleanProperty(normalized, "reWriteBatchedInserts");
        return normalized;
    }

    @Override
    protected void validateProperties(Map<String, String> properties) {
        validateNonNegativeIntegerProperty(properties, "connectTimeout");
        validateNonNegativeIntegerProperty(properties, "socketTimeout");
        validateNonBlankProperty(properties, "ApplicationName");
        validateNonBlankProperty(properties, "currentSchema");
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }
}
