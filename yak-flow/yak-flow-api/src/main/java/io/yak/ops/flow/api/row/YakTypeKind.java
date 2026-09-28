package io.yak.ops.flow.api.row;

/**
 * YakFlow 逻辑类型类别，只表达类型族，不承载 DECIMAL 等参数化类型的具体属性。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public enum YakTypeKind {
    BOOLEAN,
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,
    STRING,
    BINARY,
    DATE,
    TIME,
    TIMESTAMP,
    TIMESTAMP_WITH_TIME_ZONE
}
