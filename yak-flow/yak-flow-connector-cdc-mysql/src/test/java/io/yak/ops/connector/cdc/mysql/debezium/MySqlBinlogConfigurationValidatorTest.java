package io.yak.ops.connector.cdc.mysql.debezium;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Row-image requirements are checked before any Debezium engine starts. */
class MySqlBinlogConfigurationValidatorTest {
    @Test
    void acceptsFullRowImagesWithRowBinlogFormat() {
        assertDoesNotThrow(() -> MySqlBinlogConfigurationValidator.validateSettings(
                Map.of("binlog_format", "ROW", "binlog_row_image", "FULL", "binlog_row_value_options", "")));
        assertDoesNotThrow(() -> MySqlBinlogConfigurationValidator.validateSettings(
                Map.of("binlog_format", "row", "binlog_row_image", "full")));
    }

    @Test
    void rejectsStatementBasedBinlogsAndPartialRowImages() {
        assertInvalid(Map.of("binlog_format", "MIXED", "binlog_row_image", "FULL"), "binlog_format=ROW");
        assertInvalid(Map.of("binlog_format", "ROW", "binlog_row_image", "MINIMAL"), "binlog_row_image=FULL");
        assertInvalid(Map.of("binlog_format", "ROW"), "binlog_row_image=FULL");
        assertInvalid(
                Map.of(
                        "binlog_format", "ROW",
                        "binlog_row_image", "FULL",
                        "binlog_row_value_options", "PARTIAL_JSON"),
                "binlog_row_value_options");
    }

    private static void assertInvalid(Map<String, String> values, String expected) {
        var error = assertThrows(
                IllegalArgumentException.class, () -> MySqlBinlogConfigurationValidator.validateSettings(values));
        assertTrue(error.getMessage().contains(expected));
    }
}
