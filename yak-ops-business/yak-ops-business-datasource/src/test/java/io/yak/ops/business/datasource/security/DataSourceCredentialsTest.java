package io.yak.ops.business.datasource.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies secret redaction and credential retention at the datasource edit boundary. */
class DataSourceCredentialsTest {

    private final DataSourceCredentials credentials = new DataSourceCredentials();
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

        String result = credentials.maskForDisplay(descriptor, json);
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
        String result = credentials.maskText(descriptor, url);

        assertFalse(result.contains("oracle-secret"));
        assertTrue(result.contains("PASSWORD=******"));
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

        var result = JSONUtils.readTree(credentials.retainOnUpdate(descriptor, submitted, stored));
        assertEquals("stored-password", result.path("password").asText());
        assertEquals("new-host", result.path("host").asText());
        assertEquals("stored-token", result.path("properties").path("secretAccessKey").asText());
        assertEquals("disable", result.path("properties").path("sslmode").asText());
        assertEquals("stored-ssh", result.path("sshTunnel").path("password").asText());
    }

    @Test
    void honorsExplicitCredentialReplacement() {
        var result = JSONUtils.readTree(credentials.retainOnUpdate(
                descriptor, "{\"password\":\"new-password\"}", "{\"password\":\"old-password\"}"));
        assertEquals("new-password", result.path("password").asText());
    }
}
