package io.yak.ops.flow.api.row;

/**
 * YakFlow DECIMAL 逻辑类型；JDBC Catalog 无法提供精度时允许 precision / scale 保持未知。
 *
 * @param precision 总有效数字位数；未知时为 null
 * @param scale 小数位数；未知时为 null
 * @author weifuwan
 * @since 2026-09-28
 */
public record YakDecimalType(Integer precision, Integer scale) implements YakDataType {

    public YakDecimalType {
        if (precision != null && precision <= 0) {
            throw new IllegalArgumentException("precision must be greater than 0");
        }
        if (scale != null && scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        if (precision != null && scale != null && scale > precision) {
            throw new IllegalArgumentException("scale must not be greater than precision");
        }
    }

    @Override
    public YakTypeKind kind() {
        return YakTypeKind.DECIMAL;
    }
}
