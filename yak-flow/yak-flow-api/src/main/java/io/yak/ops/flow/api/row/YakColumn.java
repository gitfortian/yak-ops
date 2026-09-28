package io.yak.ops.flow.api.row;

import java.util.Objects;

/**
 * 描述 YakFlow 表结构中的一个逻辑字段。
 *
 * @param name 字段名称
 * @param dataType YakFlow 完整逻辑数据类型
 * @param nullable 是否允许空值
 * @param length 字符串或二进制字段容量；不适用或未知时为 null
 * @author weifuwan
 * @since 2026-09-27
 */
public record YakColumn(String name, YakDataType dataType, boolean nullable, Integer length) {

    public YakColumn {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (length != null && length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
    }
}
