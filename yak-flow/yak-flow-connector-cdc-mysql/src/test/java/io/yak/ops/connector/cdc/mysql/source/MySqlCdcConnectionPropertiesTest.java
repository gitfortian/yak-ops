package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.debezium.connector.mysql.jdbc.MySqlConnectionConfiguration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Driver settings are connection-local and identical for Snapshot and Debezium. */
class MySqlCdcConnectionPropertiesTest {
    private static final TableId TABLE = new TableId("app", null, "orders");
    private static final TableSchema SCHEMA =
            new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void usesUtcSessionsWhenServerTimezoneIsUnknown() {
        var config = config(Map.of());
        assertEquals("+00:00", config.connectionProperties().get("connectionTimeZone"));
        assertEquals("true", config.connectionProperties().get("forceConnectionTimeZoneToSession"));
        var debezium = config.debeziumProperties("session", Path.of("history.dat"));
        assertEquals("+00:00", debezium.getProperty("driver.connectionTimeZone"));
        assertEquals("true", debezium.getProperty("driver.forceConnectionTimeZoneToSession"));
        assertEquals("root", debezium.getProperty("database.user"));
        assertEffectiveTimeZone(debezium, "+00:00");
    }

    @Test
    void honorsExplicitRegionAndDriverSettingsWithoutForcingSession() {
        var config = config(Map.of(
                "connectionTimeZone", "Europe/Berlin",
                "sslMode", "VERIFY_IDENTITY",
                "connectTimeout", "8000"));
        assertEquals("Europe/Berlin", config.connectionProperties().get("connectionTimeZone"));
        assertFalse(config.connectionProperties().containsKey("forceConnectionTimeZoneToSession"));
        var debezium = config.debeziumProperties("session", Path.of("history.dat"));
        assertEquals("Europe/Berlin", debezium.getProperty("driver.connectionTimeZone"));
        assertEquals("VERIFY_IDENTITY", debezium.getProperty("driver.sslMode"));
        assertEquals("8000", debezium.getProperty("driver.connectTimeout"));
        assertEffectiveTimeZone(debezium, "Europe/Berlin");
    }

    @Test
    void rejectsConflictingAndUnsafeConnectionProperties() {
        assertThrows(IllegalArgumentException.class, () -> config(Map.of(
                "connectionTimeZone", "UTC", "forceConnectionTimeZoneToSession", "false")));
        // Explicit timezone remains in charge of the session; no extra defaults.
        assertFalse(config(Map.of("connectionTimeZone", "Asia/Shanghai"))
                .connectionProperties()
                .containsKey("forceConnectionTimeZoneToSession"));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of(
                "forceConnectionTimeZoneToSession", "false")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("password", "override")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("database.hostname", "wrong-host")));
        assertTrue(config(Map.of()).toString().contains("credentials=redacted"));
    }

    @Test
    void honorsDatasourceTimezoneInDebeziumConnectionConfiguration() {
        var config = config(Map.of("connectionTimeZone", "Asia/Shanghai", "sslMode", "REQUIRED"));
        var properties = config.debeziumProperties("session", Path.of("history.dat"));
        assertEquals("Asia/Shanghai", properties.getProperty("driver.connectionTimeZone"));
        assertEquals("REQUIRED", properties.getProperty("driver.sslMode"));
        assertEffectiveTimeZone(properties, "Asia/Shanghai");
    }

    private static void assertEffectiveTimeZone(Properties properties, String expected) {
        Properties connector = new Properties();
        connector.putAll(properties);
        connector.setProperty("database.ssl.mode", "disabled");
        connector.setProperty("connect.timeout.ms", "30000");
        var jdbc = new MySqlConnectionConfiguration(io.debezium.config.Configuration.from(connector)).config();
        assertEquals(expected, jdbc.getString("connectionTimeZone"));
    }

    private static MySqlCdcSourceConfig config(Map<String, String> properties) {
        return new MySqlCdcSourceConfig(
                "localhost", 3306, "root", "secret", 1234, "source",
                Map.of(TABLE, SCHEMA), 256, properties);
    }
}
