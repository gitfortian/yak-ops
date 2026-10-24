package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Converts JDBC values to and from YakFlow rows using an already resolved schema.
 *
 * <p>Source metadata is resolved separately by {@link JdbcTypeConverter}. The row converter
 * checks value bounds and precision without lossy rounding or truncation.
 */
public abstract class AbstractDialectConverter implements JdbcDialectConverter {

    private final TableSchema schema;

    protected AbstractDialectConverter(TableSchema schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
        for (Column column : schema.columns()) {
            if (!column.dataType().isResolved()) {
                throw new IllegalArgumentException("Unresolved JDBC target column: " + column.name());
            }
        }
    }

    @Override
    public final TableSchema schema() {
        return schema;
    }

    @Override
    public final RowData toInternal(ResultSet resultSet) throws SQLException {
        GenericRowData row = new GenericRowData(schema.columnCount());
        for (int index = 0; index < schema.columnCount(); index++) {
            LogicalType type = schema.column(index).dataType();
            Object value = readField(resultSet, index + 1, type);
            if (value == null && !type.isNullable()) {
                throw new SQLDataException("Null value in non-null JDBC field: "
                        + schema.column(index).name());
            }
            row.setField(index, value);
        }
        return row;
    }

    @Override
    public final void toExternal(RowData row, PreparedStatement statement) throws SQLException {
        if (row.getArity() != schema.columnCount()) {
            throw new SQLDataException("JDBC statement field count does not match RowData arity");
        }
        for (int index = 0; index < schema.columnCount(); index++) {
            LogicalType type = schema.column(index).dataType();
            int position = index + 1;
            if (row.isNullAt(index)) {
                if (!type.isNullable()) {
                    throw new SQLDataException("Null value in required JDBC field: "
                            + schema.column(index).name());
                }
                statement.setNull(position, sqlType(type));
                continue;
            }
            try {
                switch (type.getTypeRoot()) {
                    case BOOLEAN -> statement.setBoolean(position, row.getBoolean(index));
                    case TINYINT -> statement.setByte(position, row.getByte(index));
                    case SMALLINT -> statement.setShort(position, row.getShort(index));
                    case INTEGER -> statement.setInt(position, row.getInt(index));
                    case BIGINT -> statement.setLong(position, row.getLong(index));
                    case FLOAT -> statement.setFloat(position, row.getFloat(index));
                    case DOUBLE -> statement.setDouble(position, row.getDouble(index));
                    case DECIMAL -> bindDecimal(row, statement, index, position, (DecimalType) type);
                    case CHAR, VARCHAR -> statement.setString(position, row.getString(index));
                    case BINARY, VARBINARY -> statement.setBytes(position, row.getBinary(index));
                    case DATE -> statement.setDate(position, java.sql.Date.valueOf(row.getDate(index)));
                    case TIME_WITHOUT_TIME_ZONE -> statement.setObject(position, row.getTime(index, 9));
                    case TIMESTAMP_WITHOUT_TIME_ZONE ->
                        statement.setTimestamp(position, java.sql.Timestamp.valueOf(row.getTimestamp(index, 9)));
                    case TIMESTAMP_WITH_TIME_ZONE -> statement.setObject(position, row.getZonedTimestamp(index, 9));
                }
            } catch (ClassCastException | ArithmeticException exception) {
                throw new SQLDataException(
                        "Invalid internal JDBC value for field "
                                + schema.column(index).name(),
                        exception);
            }
        }
    }

    private static void bindDecimal(RowData row, PreparedStatement statement, int index, int position, DecimalType type)
            throws SQLException {
        BigDecimal amount = row.getDecimal(index, type.precision(), type.scale());
        statement.setBigDecimal(position, decimalValue(amount, type));
    }

    /** Converts a JDBC ResultSet field to the exact Core representation for its logical type. */
    protected Object readField(ResultSet resultSet, int position, LogicalType type) throws SQLException {
        Object raw = resultSet.getObject(position);
        if (raw == null) {
            return null;
        }
        try {
            return switch (type.getTypeRoot()) {
                case BOOLEAN -> resultSet.getBoolean(position);
                case TINYINT -> byteValue(raw);
                case SMALLINT -> shortValue(raw);
                case INTEGER -> integerValue(raw);
                case BIGINT -> longValue(raw);
                case FLOAT -> numeric(raw).floatValue();
                case DOUBLE -> numeric(raw).doubleValue();
                case DECIMAL -> decimalValue(resultSet.getBigDecimal(position), (DecimalType) type);
                case CHAR, VARCHAR -> resultSet.getString(position);
                case BINARY, VARBINARY -> resultSet.getBytes(position);
                case DATE -> localDate(raw, resultSet, position);
                case TIME_WITHOUT_TIME_ZONE -> localTime(raw, resultSet, position);
                case TIMESTAMP_WITHOUT_TIME_ZONE -> localDateTime(raw, resultSet, position);
                case TIMESTAMP_WITH_TIME_ZONE -> offsetDateTime(raw, resultSet, position);
            };
        } catch (ArithmeticException | ClassCastException exception) {
            throw new SQLDataException("JDBC value cannot be represented as " + type.asSerializableString(), exception);
        }
    }

    protected static byte byteValue(Object value) {
        return numeric(value).byteValueExact();
    }

    protected static short shortValue(Object value) {
        return numeric(value).shortValueExact();
    }

    protected static int integerValue(Object value) {
        return numeric(value).intValueExact();
    }

    protected static long longValue(Object value) {
        return numeric(value).longValueExact();
    }

    protected static BigDecimal numeric(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new ClassCastException("Expected numeric value");
    }

    private static BigDecimal decimalValue(BigDecimal decimal, DecimalType type) throws SQLDataException {
        if (decimal == null) {
            return null;
        }
        try {
            BigDecimal value = decimal.setScale(type.scale(), RoundingMode.UNNECESSARY);
            if (value.precision() > type.precision()) {
                throw new SQLDataException("JDBC decimal exceeds declared precision");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new SQLDataException("JDBC decimal requires precision-losing rounding", exception);
        }
    }

    private static LocalDate localDate(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        java.sql.Date date = resultSet.getDate(index);
        return date == null ? null : date.toLocalDate();
    }

    private static LocalTime localTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalTime time) {
            return time;
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        Object mapped = resultSet.getObject(index, LocalTime.class);
        return (LocalTime) mapped;
    }

    private static LocalDateTime localDateTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        java.sql.Timestamp timestamp = resultSet.getTimestamp(index);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static OffsetDateTime offsetDateTime(Object value, ResultSet resultSet, int index) throws SQLException {
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        return resultSet.getObject(index, OffsetDateTime.class);
    }

    private static int sqlType(LogicalType type) {
        return switch (type.getTypeRoot()) {
            case BOOLEAN -> Types.BOOLEAN;
            case TINYINT -> Types.TINYINT;
            case SMALLINT -> Types.SMALLINT;
            case INTEGER -> Types.INTEGER;
            case BIGINT -> Types.BIGINT;
            case FLOAT -> Types.REAL;
            case DOUBLE -> Types.DOUBLE;
            case DECIMAL -> Types.DECIMAL;
            case CHAR -> Types.CHAR;
            case VARCHAR -> Types.VARCHAR;
            case BINARY -> Types.BINARY;
            case VARBINARY -> Types.VARBINARY;
            case DATE -> Types.DATE;
            case TIME_WITHOUT_TIME_ZONE -> Types.TIME;
            case TIMESTAMP_WITHOUT_TIME_ZONE -> Types.TIMESTAMP;
            case TIMESTAMP_WITH_TIME_ZONE -> Types.TIMESTAMP_WITH_TIMEZONE;
        };
    }
}
