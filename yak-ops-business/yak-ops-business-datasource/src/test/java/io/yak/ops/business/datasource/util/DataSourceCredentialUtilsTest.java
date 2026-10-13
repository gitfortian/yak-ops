package io.yak.ops.business.datasource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies secret redaction and credential retention at the datasource edit boundary. */
class DataSourceCredentialUtilsTest {

    private final DataSourcePluginDescriptor descriptor = new DataSourcePluginDescriptor(
            "MYSQL", Set.of(), DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of("apiCredential"));

    @Test
    void masksNestedCredentialsAndSensitiveJdbcParameters() {
        String json = """
                {
                  "password": "database-secret",
                  "jdbcUrl": "jdbc:mysql://localhost/test?password=url-secret&secretAccessKey=access-secret",
                  "properties": {"secretAccessKey": "property-secret", "apiCredential": "provider-secret"},
                  "sshTunnel": {"password": "ssh-secret", "privateKey": "key-secret"}
                }
                """;

        String result = DataSourceCredentialUtils.maskForDisplay(descriptor, json);
        assertFalse(result.contains("database-secret"));
        assertFalse(result.contains("url-secret"));
        assertFalse(result.contains("access-secret"));
        assertFalse(result.contains("property-secret"));
        assertFalse(result.contains("provider-secret"));
        assertFalse(result.contains("ssh-secret"));
        assertFalse(result.contains("key-secret"));
        assertTrue(result.contains("password=******"));
        var displayed = JSONUtils.readTree(result);
        assertEquals("******", displayed.path("properties").path("apiCredential").asText());
    }

    @Test
    void masksCredentialsInsideOracleJdbcDescriptors() {
        String url = "jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_DATA=(PASSWORD=oracle-secret)))";
        String result = DataSourceCredentialUtils.maskText(descriptor, url);

        assertFalse(result.contains("oracle-secret"));
        assertTrue(result.contains("PASSWORD=******"));
    }

    @Test
    void retainsMaskedParametersWhenEditingJdbcUrls() {
        String stored = """
                {
                  "jdbcUrl": "jdbc:mysql://old-host/db?password=stored-password&sslmode=require"
                }
                """;
        String submitted = """
                {
                  "jdbcUrl": "jdbc:mysql://new-host/db?password=******&sslmode=disable"
                }
                """;

        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(descriptor, submitted, stored));
        assertEquals(
                "jdbc:mysql://new-host/db?password=stored-password&sslmode=disable",
                result.path("jdbcUrl").asText());
    }

    @Test
    void retainsMaskedOracleDescriptorPasswords() {
        String stored = """
                {"jdbcUrl":"jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_DATA=(PASSWORD=stored-secret))(HOST=old))"}
                """;
        String submitted = """
                {"jdbcUrl":"jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_DATA=(PASSWORD=******))(HOST=new))"}
                """;

        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(descriptor, submitted, stored));
        assertEquals(
                "jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_DATA=(PASSWORD=stored-secret))(HOST=new))",
                result.path("jdbcUrl").asText());
    }

    @Test
    void retainsOnlyUnchangedCredentialsDuringEditing() {
        String stored = """
                {
                  "password": "stored-password",
                  "host": "old-host",
                  "properties": {"secretAccessKey": "stored-token", "sslmode": "require"},
                  "sshTunnel": {"password": "stored-ssh"}
                }
                """;
        String submitted = """
                {
                  "password": "******",
                  "host": "new-host",
                  "properties": {"secretAccessKey": "", "sslmode": "disable"},
                  "sshTunnel": {"password": null}
                }
                """;

        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(descriptor, submitted, stored));
        assertEquals("stored-password", result.path("password").asText());
        assertEquals("new-host", result.path("host").asText());
        assertEquals("stored-token", result.path("properties").path("secretAccessKey").asText());
        assertEquals("disable", result.path("properties").path("sslmode").asText());
        assertEquals("stored-ssh", result.path("sshTunnel").path("password").asText());
    }

    @Test
    void honorsExplicitCredentialReplacement() {
        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(
                descriptor, "{\"password\":\"new-password\"}", "{\"password\":\"old-password\"}"));
        assertEquals("new-password", result.path("password").asText());
    }

    @Test
    void masksPostgreSqlAdvancedPropertiesAndJdbcParameters() {
        DataSourcePluginDescriptor pg = new DataSourcePluginDescriptor(
                "POSTGRE_SQL", Set.of(), DataSourcePluginDescriptor.CURRENT_API_VERSION, Set.of(), Set.of());
        String json = """
                {
                  "jdbcUrl": "jdbc:postgresql://localhost:5432/orders?password=pg-secret&sslmode=require",
                  "properties": {"client_secret": "property-secret", "applicationName": "yak-ops"}
                }
                """;

        String masked = DataSourceCredentialUtils.maskForDisplay(pg, json);

        assertFalse(masked.contains("pg-secret"));
        assertFalse(masked.contains("property-secret"));
        assertTrue(masked.contains("password=******"));
        assertEquals("******", JSONUtils.readTree(masked).path("properties").path("client_secret").asText());
        assertEquals("yak-ops", JSONUtils.readTree(masked).path("properties").path("applicationName").asText());
    }

    @Test
    void retainsEmbeddedCredentialsInAdvancedPropertyValues() {
        String stored = """
                {"properties":{"sessionVariables":"password=stored-secret&mode=old"}}
                """;
        String edited = """
                {"properties":{"sessionVariables":"password=******&mode=new"}}
                """;

        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(descriptor, edited, stored));

        assertEquals(
                "password=stored-secret&mode=new",
                result.path("properties").path("sessionVariables").asText());
    }

    @Test
    void leavesUnrelatedMaskLiteralsInJdbcUrlsUntouched() {
        String stored = """
                {"jdbcUrl":"jdbc:mysql://localhost/db?password=old-password&note=old"}
                """;
        String edited = """
                {"jdbcUrl":"jdbc:mysql://localhost/db?password=******&note=******"}
                """;

        var result = JSONUtils.readTree(DataSourceCredentialUtils.retainOnUpdate(descriptor, edited, stored));

        assertEquals(
                "jdbc:mysql://localhost/db?password=old-password&note=******",
                result.path("jdbcUrl").asText());
    }

    @Test
    void rejectsMaskedUrlCredentialsWithoutAStoredValue() {
        String stored = """
                {"jdbcUrl":"jdbc:mysql://localhost/db?sslmode=require"}
                """;
        String edited = """
                {"jdbcUrl":"jdbc:mysql://localhost/db?password=******"}
                """;

        DataSourceException failure = assertThrows(
                DataSourceException.class,
                () -> DataSourceCredentialUtils.retainOnUpdate(descriptor, edited, stored));
        assertEquals(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, failure.getErrorCode());
    }

    @Test
    void rejectsUnsupportedConnectionArraysInsteadOfExposingCredentials() {
        String connection = """
                {"options":[{"password":"unexpected-secret"}]}
                """;

        DataSourceException failure = assertThrows(
                DataSourceException.class, () -> DataSourceCredentialUtils.maskForDisplay(descriptor, connection));
        assertEquals(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, failure.getErrorCode());
    }

}
