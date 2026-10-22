package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Driver settings are connection-local and identical for Snapshot and Debezium. */
class MySqlCdcConnectionPropertiesTest {
    private static final TableId TABLE = new TableId("app", null, "orders");
    private static final TableSchema SCHEMA =
            new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void usesUtcSessionsWhenServerTimezoneIsUnknown() {
        var config = config(Map.of());
        assertEquals("UTC", config.connectionProperties().get("connectionTimeZone"));
        assertEquals("true", config.connectionProperties().get("forceConnectionTimeZoneToSession"));
        var debezium = config.debeziumProperties("session", Path.of("history.dat"));
        assertEquals("UTC", debezium.getProperty("driver.connectionTimeZone"));
        assertEquals("true", debezium.getProperty("driver.forceConnectionTimeZoneToSession"));
        assertEquals("root", debezium.getProperty("database.user"));
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
    }

    @Test
    void rejectsConflictingAndUnsafeConnectionProperties() {
        assertThrows(IllegalArgumentException.class, () -> config(Map.of(
                "connectionTimeZone", "UTC", "serverTimezone", "Asia/Shanghai")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of(
                "forceConnectionTimeZoneToSession", "false")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("password", "override")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("database.hostname", "wrong-host")));
        assertTrue(config(Map.of()).toString().contains("credentials=redacted"));
    }

    private static MySqlCdcSourceConfig config(Map<String, String> properties) {
        return new MySqlCdcSourceConfig(
                "localhost", 3306, "root", "secret", 1234, "source",
                Map.of(TABLE, SCHEMA), 256, properties);
    }
}
