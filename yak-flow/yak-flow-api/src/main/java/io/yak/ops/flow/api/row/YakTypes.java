package io.yak.ops.flow.api.row;

/**
 * YakFlow 内置逻辑类型常量与参数化类型工厂。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public final class YakTypes {

    public static final YakDataType BOOLEAN = new YakBasicType(YakTypeKind.BOOLEAN);
    public static final YakDataType TINYINT = new YakBasicType(YakTypeKind.TINYINT);
    public static final YakDataType SMALLINT = new YakBasicType(YakTypeKind.SMALLINT);
    public static final YakDataType INTEGER = new YakBasicType(YakTypeKind.INTEGER);
    public static final YakDataType BIGINT = new YakBasicType(YakTypeKind.BIGINT);
    public static final YakDataType FLOAT = new YakBasicType(YakTypeKind.FLOAT);
    public static final YakDataType DOUBLE = new YakBasicType(YakTypeKind.DOUBLE);
    public static final YakDataType STRING = new YakBasicType(YakTypeKind.STRING);
    public static final YakDataType BINARY = new YakBasicType(YakTypeKind.BINARY);
    public static final YakDataType DATE = new YakBasicType(YakTypeKind.DATE);
    public static final YakDataType TIME = new YakBasicType(YakTypeKind.TIME);
    public static final YakDataType TIMESTAMP = new YakBasicType(YakTypeKind.TIMESTAMP);
    public static final YakDataType TIMESTAMP_WITH_TIME_ZONE = new YakBasicType(YakTypeKind.TIMESTAMP_WITH_TIME_ZONE);

    private YakTypes() {}

    public static YakDecimalType decimal(Integer precision, Integer scale) {
        return new YakDecimalType(precision, scale);
    }
}
