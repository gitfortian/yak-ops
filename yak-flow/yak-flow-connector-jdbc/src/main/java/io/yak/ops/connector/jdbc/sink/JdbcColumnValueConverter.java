package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts source RowData values to exact target logical representations before JDBC binding.
 *
 * <p>Only compatibility rules with a lossless Java conversion are allowed. Schema validation
 * and runtime projection share {@link #supports(Column, Column)} so a SQL type mapping never
 * claims support for a value conversion that the Sink cannot perform.
 */
final class JdbcColumnValueConverter {

    private JdbcColumnValueConverter() {}

    static boolean supports(Column source, Column target) {
        if (!source.dataType().isResolved()
                || !target.dataType().isResolved()
                || !JdbcSchemaCompatibility.isCompatible(source, target)) {
            return false;
        }
        LogicalTypeRoot from = source.dataType().getTypeRoot();
        LogicalTypeRoot to = target.dataType().getTypeRoot();
        if (from == to) return true;
        if (from == LogicalTypeRoot.CHAR && to == LogicalTypeRoot.VARCHAR) return true;
        if (from == LogicalTypeRoot.BINARY && to == LogicalTypeRoot.VARBINARY) return true;
        if (isInteger(from) && (isInteger(to) || to == LogicalTypeRoot.DECIMAL)) return true;
        if (from == LogicalTypeRoot.BOOLEAN && (isInteger(to) || to == LogicalTypeRoot.DECIMAL)) return true;
        if (from == LogicalTypeRoot.FLOAT && to == LogicalTypeRoot.DOUBLE) return true;
        return from == LogicalTypeRoot.DATE && to == LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE;
    }

    static Object convert(RowData row, int position, Column source, Column target) {
        if (row.isNullAt(position)) {
            if (!target.nullable()) {
                throw new IllegalArgumentException("Null value in required JDBC target field: " + target.name());
            }
            return null;
        }
        LogicalTypeRoot from = source.dataType().getTypeRoot();
        LogicalTypeRoot to = target.dataType().getTypeRoot();
        try {
            if (from == to) {
                if (to == LogicalTypeRoot.DECIMAL) {
                    return decimal(row.getDecimal(position, ((DecimalType) source.dataType()).precision(),
                            ((DecimalType) source.dataType()).scale()), (DecimalType) target.dataType());
                }
                return row.getField(position);
            }
            if (from == LogicalTypeRoot.CHAR && to == LogicalTypeRoot.VARCHAR) {
                return row.getString(position);
            }
            if (from == LogicalTypeRoot.BINARY && to == LogicalTypeRoot.VARBINARY) {
                return row.getBinary(position);
            }
            if (from == LogicalTypeRoot.FLOAT && to == LogicalTypeRoot.DOUBLE) {
                return (double) row.getFloat(position);
            }
            if (from == LogicalTypeRoot.DATE && to == LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE) {
                return row.getDate(position).atStartOfDay();
            }
            if (isInteger(from) || from == LogicalTypeRoot.BOOLEAN) {
                long value = from == LogicalTypeRoot.BOOLEAN ? (row.getBoolean(position) ? 1L : 0L)
                        : integer(row, position, from);
                return switch (to) {
                    case TINYINT -> narrowByte(value, target);
                    case SMALLINT -> narrowShort(value, target);
                    case INTEGER -> Math.toIntExact(value);
                    case BIGINT -> value;
                    case DECIMAL -> decimal(BigDecimal.valueOf(value), (DecimalType) target.dataType());
                    default -> throw new IllegalArgumentException("Unsupported JDBC target type: " + target.name());
                };
            }
            throw new IllegalArgumentException("Unsupported JDBC field conversion: " + target.name());
        } catch (ClassCastException | ArithmeticException error) {
            throw new IllegalArgumentException("Invalid JDBC value for target field: " + target.name(), error);
        }
    }

    private static byte narrowByte(long value, Column target) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("JDBC integer value out of range for target field: " + target.name());
        }
        return (byte) value;
    }

    private static short narrowShort(long value, Column target) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("JDBC integer value out of range for target field: " + target.name());
        }
        return (short) value;
    }

    private static long integer(RowData row, int position, LogicalTypeRoot type) {
        return switch (type) {
            case TINYINT -> row.getByte(position);
            case SMALLINT -> row.getShort(position);
            case INTEGER -> row.getInt(position);
            case BIGINT -> row.getLong(position);
            default -> throw new IllegalArgumentException("Not an integer source type");
        };
    }

    private static boolean isInteger(LogicalTypeRoot root) {
        return root == LogicalTypeRoot.TINYINT
                || root == LogicalTypeRoot.SMALLINT
                || root == LogicalTypeRoot.INTEGER
                || root == LogicalTypeRoot.BIGINT;
    }

    private static BigDecimal decimal(BigDecimal value, DecimalType target) {
        BigDecimal scaled = value.setScale(target.scale(), RoundingMode.UNNECESSARY);
        if (scaled.precision() > target.precision()) {
            throw new IllegalArgumentException("JDBC decimal exceeds target precision");
        }
        return scaled;
    }
}
