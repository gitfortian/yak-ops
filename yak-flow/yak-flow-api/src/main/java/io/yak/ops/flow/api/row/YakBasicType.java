package io.yak.ops.flow.api.row;

import java.util.Objects;

/**
 * 无额外参数的 YakFlow 基础逻辑类型。
 *
 * @param kind 基础类型类别
 * @author weifuwan
 * @since 2026-09-28
 */
public record YakBasicType(YakTypeKind kind) implements YakDataType {

    public YakBasicType {
        Objects.requireNonNull(kind, "kind must not be null");
        if (kind == YakTypeKind.DECIMAL) {
            throw new IllegalArgumentException("DECIMAL must use YakDecimalType");
        }
    }
}
