package io.yak.ops.flow.api.row;

import java.util.Objects;

/**
 * 描述 YakFlow 表结构中的一个逻辑字段以及跨 JDBC 数据库映射所需的基础精度信息。
 *
 * @param name 字段名称
 * @param dataType YakFlow 逻辑数据类型
 * @param nullable 是否允许空值
 * @param length 字符串或二进制字段长度；不适用时为 null
 * @param precision 数值或时间字段精度；不适用时为 null
 * @param scale DECIMAL 小数位数；不适用时为 null
 * @author weifuwan
 * @since 2026-09-27
 */
public record YakColumn(
        String name, YakDataType dataType, boolean nullable, Integer length, Integer precision, Integer scale) {

    public YakColumn {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        requireNonNegative(length, "length");
        requireNonNegative(precision, "precision");
        requireNonNegative(scale, "scale");
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
