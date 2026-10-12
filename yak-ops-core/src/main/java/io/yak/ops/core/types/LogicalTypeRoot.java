package io.yak.ops.core.types;

/**
 * Defines the logical type families supported by YakFlow.
 *
 * <p>Vendor SQL type names and JDBC {@code java.sql.Types} codes are not logical type roots.
 */
public enum LogicalTypeRoot {
    BOOLEAN,
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,
    CHAR,
    VARCHAR,
    BINARY,
    VARBINARY,
    DATE,
    TIME_WITHOUT_TIME_ZONE,
    TIMESTAMP_WITHOUT_TIME_ZONE,
    TIMESTAMP_WITH_TIME_ZONE
}
