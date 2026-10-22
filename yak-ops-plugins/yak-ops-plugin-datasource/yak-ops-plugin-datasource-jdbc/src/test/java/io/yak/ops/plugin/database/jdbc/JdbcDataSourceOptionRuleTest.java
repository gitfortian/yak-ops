package io.yak.ops.plugin.database.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.rule.OptionRuleMetadata;
import io.yak.ops.configuration.validation.OptionValidationException;
import io.yak.ops.plugin.database.jdbc.mysql.MySqlDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.oracle.OracleDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.postgresql.PostgreSqlDataSourcePlugin;
import org.junit.jupiter.api.Test;

/** Verifies provider-owned options, SSH conditions and normalized connection round-trips. */
class JdbcDataSourceOptionRuleTest {

    private final MySqlDataSourcePlugin mysql = new MySqlDataSourcePlugin();
    private final PostgreSqlDataSourcePlugin postgres = new PostgreSqlDataSourcePlugin();
    private final OracleDataSourcePlugin oracle = new OracleDataSourcePlugin();

    @Test
    void eachVendorPublishesItsPortDefaultsAndMySqlDriverChoices() {
        assertEquals(3306, defaultPort(mysql));
        assertEquals(5432, defaultPort(postgres));
        assertEquals(1521, defaultPort(oracle));
        var driver = OptionRuleMetadata.describe(mysql.optionRule()).stream()
                .filter(option -> option.key().equals("driverId"))
                .findFirst()
                .orElseThrow();
        assertEquals("AUTO", driver.defaultValue());
        assertEquals(java.util.List.of("AUTO", "MYSQL_8", "MYSQL_5"), driver.allowedValues());
        var password = OptionRuleMetadata.describe(mysql.optionRule()).stream()
                .filter(option -> option.key().equals("password"))
                .findFirst()
                .orElseThrow();
        assertNull(password.defaultValue());
    }

    @Test
    void roundTripsNormalizedMySqlSettingsWithoutDroppingConnectionFields() {
        JdbcConnectionProperties first = (JdbcConnectionProperties) mysql.parseConnection(ConfigParser.parseJson("""
                {
                  "host":"db.internal",
                  "database":"sales",
                  "username":"app",
                  "password":"private-credential",
                  "driverId":"MYSQL_8",
                  "properties":{"connectionTimeZone":"Asia/Shanghai","useSSL":"FALSE"}
                }
                """));
        JdbcConnectionProperties second =
                (JdbcConnectionProperties) mysql.parseConnection(ConfigParser.parseJson(first.normalizedJson()));

        assertEquals(first.jdbcUrl(), second.jdbcUrl());
        assertEquals(first.database(), second.database());
        assertEquals(first.driverId(), second.driverId());
        assertEquals(first.properties(), second.properties());
        assertEquals("false", second.properties().get("useSSL"));
        assertEquals("Asia/Shanghai", second.properties().get("connectionTimeZone"));
        assertEquals("private-credential", second.password());
    }

    @Test
    void sshConnectionRoundTripUsesStructuredHostAndKeepsCredentialsInResourceOnly() {
        JdbcConnectionProperties first = (JdbcConnectionProperties) mysql.parseConnection(ConfigParser.parseJson("""
                {
                  "host":"db.internal",
                  "database":"sales",
                  "username":"app",
                  "sshTunnel":{
                    "enabled":true,
                    "host":"ssh.internal",
                    "username":"ops",
                    "password":"ssh-private"
                  }
                }
                """));
        JdbcConnectionProperties second =
                (JdbcConnectionProperties) mysql.parseConnection(ConfigParser.parseJson(first.normalizedJson()));

        assertTrue(first.sshTunnel().enabled());
        assertTrue(second.sshTunnel().enabled());
        assertEquals("ssh-private", second.sshTunnel().password());
        assertEquals("jdbc:mysql://db.internal:3306/sales", second.jdbcUrl());
        assertFalse(second.toString().contains("ssh-private"));
    }

    @Test
    void requiresSshCredentialsAndKnownHostsUnderTheirDeclaredConditions() {
        var missingPassword = assertThrows(OptionValidationException.class, () -> mysql.parseConnection(
                ConfigParser.parseJson("""
                        {
                          "host":"db.internal","database":"sales","username":"app",
                          "sshTunnel":{"enabled":true,"host":"ssh.internal","username":"ops"}
                        }
                        """)));
        assertTrue(missingPassword.errors().stream()
                .anyMatch(error -> error.path().equals("sshTunnel.password")));
        var missingKnownHosts = assertThrows(OptionValidationException.class, () -> mysql.parseConnection(
                ConfigParser.parseJson("""
                        {
                          "host":"db.internal","database":"sales","username":"app",
                          "sshTunnel":{"enabled":true,"host":"ssh.internal","username":"ops",
                            "password":"private-secret","strictHostKeyChecking":true}
                        }
                        """)));
        assertTrue(missingKnownHosts.errors().stream()
                .anyMatch(error -> error.path().equals("sshTunnel.knownHosts")));
        assertFalse(missingKnownHosts.getMessage().contains("private-secret"));
    }

    @Test
    void rejectsUnknownSshFieldsWithoutRejectingVendorProperties() {
        var invalid = assertThrows(OptionValidationException.class, () -> mysql.parseConnection(
                ConfigParser.parseJson("""
                        {
                          "host":"db.internal","database":"sales","username":"app",
                          "sshTunnel":{"enabled":false,"unexpected":"private-secret"},
                          "properties":{"custom.vendor.setting":"42"}
                        }
                        """)));
        assertTrue(invalid.errors().stream().anyMatch(error -> error.path().equals("sshTunnel.unexpected")));
        assertFalse(invalid.getMessage().contains("private-secret"));

        var valid = mysql.parseConnection(ConfigParser.parseJson("""
                {"host":"db.internal","database":"sales","username":"app",
                "properties":{"custom.vendor.setting":"42"}}
                """));
        assertEquals("42", valid.properties().get("custom.vendor.setting"));
    }

    @Test
    void rejectsInvalidPortAndTypedJdbcPropertyValuesWithOptionPaths() {
        var invalidPort = assertThrows(OptionValidationException.class, () -> postgres.parseConnection(
                ConfigParser.parseJson("""
                        {"host":"db.internal","port":70000,"database":"sales","username":"app"}
                        """)));
        assertTrue(invalidPort.errors().stream().anyMatch(error -> error.path().equals("port")));

        var invalidProperty = assertThrows(OptionValidationException.class, () -> postgres.parseConnection(
                ConfigParser.parseJson("""
                        {"host":"db.internal","database":"sales","username":"app",
                          "properties":{"connectTimeout":"invalid-value"}}
                        """)));
        assertTrue(invalidProperty.errors().stream()
                .anyMatch(error -> error.path().equals("properties.connectTimeout")));
        assertFalse(invalidProperty.getMessage().contains("invalid-value"));
    }

    @Test
    void postgresPreservesPropertyCanonicalizationAndValidatesChoices() {
        var connection = postgres.parseConnection(ConfigParser.parseJson("""
                {"host":"pg.internal","database":"sales","username":"app",
                  "properties":{"sslmode":"REQUIRE","tcpKeepAlive":"TRUE","connecttimeout":"3"}}
                """));
        assertEquals("require", connection.properties().get("sslmode"));
        assertEquals("true", connection.properties().get("tcpKeepAlive"));
        assertEquals("3", connection.properties().get("connectTimeout"));
        var invalid = assertThrows(OptionValidationException.class, () -> postgres.parseConnection(
                ConfigParser.parseJson("""
                        {"host":"pg.internal","database":"sales","username":"app",
                          "properties":{"sslmode":"unknown"}}
                        """)));
        assertTrue(invalid.errors().stream()
                .anyMatch(error -> error.path().equals("properties.sslmode")));
    }

    @Test
    void oracleSupportsNativeUrlAndValidatesPrefetch() {
        var connection = oracle.parseConnection(ConfigParser.parseJson("""
                {"jdbcUrl":"jdbc:oracle:thin:@//oracle.internal:1521/app",
                  "username":"app","properties":{"defaultRowPrefetch":"20"}}
                """));
        assertEquals("20", connection.properties().get("defaultRowPrefetch"));
        assertEquals("jdbc:oracle:thin:@//oracle.internal:1521/app", connection.jdbcUrl());

        var invalid = assertThrows(OptionValidationException.class, () -> oracle.parseConnection(
                ConfigParser.parseJson("""
                        {"jdbcUrl":"jdbc:oracle:thin:@//oracle.internal:1521/app",
                          "username":"app","properties":{"defaultRowPrefetch":"0"}}
                        """)));
        assertTrue(invalid.errors().stream()
                .anyMatch(error -> error.path().equals("properties.defaultRowPrefetch")));
    }

    private static int defaultPort(AbstractJdbcDataSourcePlugin plugin) {
        return (Integer) OptionRuleMetadata.describe(plugin.optionRule()).stream()
                .filter(option -> option.key().equals("port"))
                .findFirst()
                .orElseThrow()
                .defaultValue();
    }
}
