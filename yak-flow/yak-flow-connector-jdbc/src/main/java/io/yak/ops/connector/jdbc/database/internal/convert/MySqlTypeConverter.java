package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

/** MySQL-specific BIT, unsigned numerics, and native type restrictions. */
public final class MySqlTypeConverter extends StandardJdbcTypeConverter {

    private static final java.util.Set<String> SPATIAL_TYPES = java.util.Set.of(
            "POINT",
            "GEOMETRY",
            "LINESTRING",
            "POLYGON",
            "MULTIPOINT",
            "MULTILINESTRING",
            "MULTIPOLYGON",
            "GEOMETRYCOLLECTION");

    @Override
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        return resolveType(
                metadata.getColumnType(index),
                metadata.getPrecision(index),
                metadata.getScale(index),
                metadata.getColumnTypeName(index),
                metadata.getColumnLabel(index));
    }

    /**
     * Resolves MySQL source types from either JDBC or Debezium's structured DDL metadata.
     *
     * <p>The CDC caller uses metadata captured at the original Binlog offset, not a live
     * Catalog lookup that might observe later DDL.
     */
    public LogicalType convertColumn(
            int jdbcType, String nativeType, int precision, int scale, boolean nullable, String column)
            throws SQLException {
        LogicalType type = resolveType(jdbcType, precision, scale, nativeType, column);
        if (!type.isResolved()) {
            throw new SQLFeatureNotSupportedException("Unsupported MySQL source type for field " + column);
        }
        return type.copy(nullable);
    }

    @Override
    protected LogicalType resolveType(int sqlType, int precision, int scale, String nativeType, String column)
            throws SQLException {
        String name = java.util.Objects.requireNonNull(nativeType, "nativeType")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);
        if (java.util.Set.of("ENUM", "SET", "JSON").contains(name)) {
            return LogicalTypes.STRING;
        }
        if (SPATIAL_TYPES.contains(name)) {
            throw new SQLFeatureNotSupportedException("Unsupported JDBC type " + name + " for field " + column);
        }
        if (sqlType == Types.TINYINT && name.contains("UNSIGNED")) {
            return LogicalTypes.SMALLINT;
        }
        if (sqlType == Types.SMALLINT && name.contains("UNSIGNED")) {
            return LogicalTypes.INTEGER;
        }
        if (sqlType == Types.INTEGER && name.contains("UNSIGNED")) {
            return LogicalTypes.BIGINT;
        }
        if (sqlType == Types.BIGINT && name.contains("UNSIGNED")) {
            return decimalType(20, 0, column);
        }
        return super.resolveType(sqlType, precision, scale, nativeType, column);
    }
}
