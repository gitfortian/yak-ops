package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.connector.jdbc.database.dialect.JdbcTypeConverter;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves standard JDBC column metadata into YakFlow logical types, without reading row values. */
public class StandardJdbcTypeConverter implements JdbcTypeConverter {

    @Override
    public final TableSchema convert(ResultSetMetaData metadata) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        List<Column> columns = new ArrayList<>(metadata.getColumnCount());
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String name = metadata.getColumnLabel(index);
            LogicalType type = resolveType(metadata, index);
            if (type == null || !type.isResolved()) {
                throw new SQLFeatureNotSupportedException(
                        "JDBC field '" + name + "' has an unsupported or unresolved logical type");
            }
            boolean nullable = metadata.isNullable(index) != ResultSetMetaData.columnNoNulls;
            columns.add(new Column(name, type.copy(nullable)));
        }
        return new TableSchema(columns, List.of());
    }

    /**
     * Resolves a JDBC result-column type without inventing a DECIMAL precision.
     *
     * <p>Unsupported nested, vendor-specific, or ambiguous SQL types must be overridden
     * explicitly in a vendor converter or rejected before creating rows.
     */
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        int sqlType = metadata.getColumnType(index);
        int precision = metadata.getPrecision(index);
        int scale = metadata.getScale(index);
        return switch (sqlType) {
            case Types.BOOLEAN -> LogicalTypes.BOOLEAN;
            case Types.BIT -> precision <= 1 ? LogicalTypes.BOOLEAN : LogicalTypes.varbinary(precision);
            case Types.TINYINT -> LogicalTypes.TINYINT;
            case Types.SMALLINT -> LogicalTypes.SMALLINT;
            case Types.INTEGER -> LogicalTypes.INTEGER;
            case Types.BIGINT -> LogicalTypes.BIGINT;
            case Types.REAL, Types.FLOAT -> LogicalTypes.FLOAT;
            case Types.DOUBLE -> LogicalTypes.DOUBLE;
            case Types.NUMERIC, Types.DECIMAL -> decimalType(precision, scale, metadata.getColumnLabel(index));
            case Types.CHAR, Types.NCHAR -> precision > 0 ? LogicalTypes.charType(precision) : LogicalTypes.STRING;
            case Types.VARCHAR, Types.NVARCHAR -> precision > 0 ? LogicalTypes.varchar(precision) : LogicalTypes.STRING;
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> LogicalTypes.STRING;
            case Types.BINARY -> precision > 0 ? LogicalTypes.fixedBinary(precision) : LogicalTypes.BINARY;
            case Types.VARBINARY -> precision > 0 ? LogicalTypes.varbinary(precision) : LogicalTypes.BINARY;
            case Types.LONGVARBINARY, Types.BLOB -> LogicalTypes.BINARY;
            case Types.DATE -> LogicalTypes.DATE;
            case Types.TIME -> LogicalTypes.time(temporalPrecision(scale));
            case Types.TIMESTAMP -> LogicalTypes.timestamp(temporalPrecision(scale));
            case Types.TIMESTAMP_WITH_TIMEZONE -> LogicalTypes.zonedTimestamp(temporalPrecision(scale));
            default ->
                throw new SQLFeatureNotSupportedException("Unsupported JDBC type " + metadata.getColumnTypeName(index)
                        + " for field " + metadata.getColumnLabel(index));
        };
    }

    protected static LogicalType decimalType(int precision, int scale, String column) throws SQLDataException {
        if (precision < 1 || precision > DecimalType.MAX_PRECISION || scale < 0 || scale > precision) {
            throw new SQLDataException("Unresolved or unsupported JDBC decimal precision for field " + column);
        }
        return LogicalTypes.decimal(precision, scale);
    }

    protected static int temporalPrecision(int precision) throws SQLDataException {
        if (precision < 0 || precision > 9) {
            throw new SQLDataException("JDBC temporal precision outside 0..9");
        }
        return precision;
    }
}
