package io.yak.ops.connector.cdc.mysql.debezium;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Validates MySQL row-based Binlog settings before starting Debezium replication. */
public final class MySqlBinlogConfigurationValidator {
    private MySqlBinlogConfigurationValidator() {}

    public static void validate(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Map<String, String> settings = new java.util.LinkedHashMap<>();
        for (String name : new String[] {"binlog_format", "binlog_row_image", "binlog_row_value_options"}) {
            try (Statement query = connection.createStatement();
                    ResultSet rows = query.executeQuery("SHOW GLOBAL VARIABLES LIKE '" + name + "'")) {
                if (rows.next()) settings.put(name, rows.getString(2));
            }
        }
        validateSettings(settings);
    }

    static void validateSettings(Map<String, String> settings) {
        require(settings, "binlog_format", "ROW");
        require(settings, "binlog_row_image", "FULL");
        String valueOptions = settings.get("binlog_row_value_options");
        if (valueOptions != null && !valueOptions.isBlank()) {
            throw new IllegalArgumentException(
                    "MySQL CDC requires binlog_row_value_options to be empty; found " + valueOptions);
        }
    }

    private static void require(Map<String, String> settings, String key, String expected) {
        String value = settings.get(key);
        if (value == null || !expected.equals(value.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "MySQL CDC requires " + key + "=" + expected + "; found " + (value == null ? "missing" : value));
        }
    }
}
