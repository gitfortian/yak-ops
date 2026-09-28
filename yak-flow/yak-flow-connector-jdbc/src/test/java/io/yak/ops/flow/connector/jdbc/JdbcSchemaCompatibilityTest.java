package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import org.junit.jupiter.api.Test;

class JdbcSchemaCompatibilityTest {

    @Test
    void shouldAllowIntegerWideningAndRejectNarrowing() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.INTEGER, 10, 0), column("target", Types.BIGINT, 19, 0)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.BIGINT, 19, 0), column("target", Types.SMALLINT, 5, 0)));
    }

    @Test
    void shouldValidateIntegerToDecimalCapacity() {
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.INTEGER, 10, 0), column("target", Types.DECIMAL, 12, 2)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.BIGINT, 19, 0), column("target", Types.DECIMAL, 18, 0)));
    }

    @Test
    void shouldRejectDecimalPrecisionOrScaleLoss() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.DECIMAL, 10, 2), column("target", Types.DECIMAL, 9, 2)));
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.DECIMAL, 10, 2), column("target", Types.DECIMAL, 12, 1)));
        assertTrue(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.DECIMAL, 10, 2), column("target", Types.DECIMAL, 12, 2)));
    }

    @Test
    void shouldRejectSmallerStringCapacity() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.VARCHAR, 100, null), column("target", Types.VARCHAR, 50, null)));
    }

    @Test
    void shouldRejectJdbcTypesUnsupportedBySchemaMapper() {
        assertFalse(JdbcSchemaCompatibility.isCompatible(
                column("source", Types.TIME_WITH_TIMEZONE, null, null),
                column("target", Types.TIME_WITH_TIMEZONE, null, null)));
    }

    private DataSourceColumn column(String name, int jdbcType, Integer size, Integer scale) {
        return new DataSourceColumn(name, name, jdbcType, size, scale, true, 1, false, null);
    }
}
