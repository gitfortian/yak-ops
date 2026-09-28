package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;

/**
 * 定义 JDBC 字段在 YakFlow bounded JDBC 同步中的可写兼容边界。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public final class JdbcSchemaCompatibility {

    private JdbcSchemaCompatibility() {}

    public static boolean isCompatible(DataSourceColumn source, DataSourceColumn target) {
        if (source == null || target == null) return false;

        YakDataType sourceType = resolveType(source);
        YakDataType targetType = resolveType(target);
        if (sourceType == null || targetType == null) return false;

        if (sourceType == targetType) {
            return sameTypeCompatible(sourceType, source, target);
        }
        if (isInteger(sourceType) && isInteger(targetType)) {
            return integerRank(sourceType) <= integerRank(targetType);
        }
        if (isInteger(sourceType) && targetType == YakDataType.DECIMAL) {
            return integerToDecimalCompatible(sourceType, source, target);
        }
        return sourceType == YakDataType.FLOAT && targetType == YakDataType.DOUBLE;
    }

    private static YakDataType resolveType(DataSourceColumn column) {
        try {
            return JdbcSchemaMapper.toYakType(column);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean sameTypeCompatible(YakDataType dataType, DataSourceColumn source, DataSourceColumn target) {
        if (dataType == YakDataType.STRING || dataType == YakDataType.BINARY) {
            return capacityCompatible(source.size(), target.size());
        }
        if (dataType == YakDataType.DECIMAL) {
            return decimalCompatible(source, target);
        }
        return true;
    }

    private static boolean integerToDecimalCompatible(
            YakDataType sourceType, DataSourceColumn source, DataSourceColumn target) {
        if (!positive(target.size())) return true;

        int targetScale = knownScale(target.scale()) ? target.scale() : 0;
        int targetIntegerDigits = target.size() - targetScale;
        int sourceIntegerDigits = positive(source.size()) ? source.size() : integerDigits(sourceType);
        return targetIntegerDigits >= sourceIntegerDigits;
    }

    private static boolean decimalCompatible(DataSourceColumn source, DataSourceColumn target) {
        if (knownScale(source.scale()) && knownScale(target.scale()) && source.scale() > target.scale()) {
            return false;
        }
        if (!positive(source.size()) || !positive(target.size())) return true;

        if (knownScale(source.scale()) && knownScale(target.scale())) {
            int sourceIntegerDigits = source.size() - source.scale();
            int targetIntegerDigits = target.size() - target.scale();
            return targetIntegerDigits >= sourceIntegerDigits;
        }
        return target.size() >= source.size();
    }

    private static boolean capacityCompatible(Integer sourceSize, Integer targetSize) {
        return !positive(sourceSize) || !positive(targetSize) || targetSize >= sourceSize;
    }

    private static boolean isInteger(YakDataType dataType) {
        return dataType == YakDataType.TINYINT
                || dataType == YakDataType.SMALLINT
                || dataType == YakDataType.INTEGER
                || dataType == YakDataType.BIGINT;
    }

    private static int integerRank(YakDataType dataType) {
        return switch (dataType) {
            case TINYINT -> 1;
            case SMALLINT -> 2;
            case INTEGER -> 3;
            case BIGINT -> 4;
            default -> throw new IllegalArgumentException("not an integer type: " + dataType);
        };
    }

    private static int integerDigits(YakDataType dataType) {
        return switch (dataType) {
            case TINYINT -> 3;
            case SMALLINT -> 5;
            case INTEGER -> 10;
            case BIGINT -> 19;
            default -> throw new IllegalArgumentException("not an integer type: " + dataType);
        };
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean knownScale(Integer value) {
        return value != null && value >= 0;
    }
}
