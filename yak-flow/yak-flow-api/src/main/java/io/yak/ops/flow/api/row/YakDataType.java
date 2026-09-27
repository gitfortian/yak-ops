package io.yak.ops.flow.api.row;

/**
 * YakFlow 跨数据库传输使用的最小逻辑类型集合，不承载数据库厂商原生类型名称。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum YakDataType {
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
