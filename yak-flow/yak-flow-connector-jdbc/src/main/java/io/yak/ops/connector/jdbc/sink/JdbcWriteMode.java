package io.yak.ops.connector.jdbc.sink;

/** JDBC write semantics. RETRACT is reserved until transactional keyless writing is implemented. */
public enum JdbcWriteMode {
    APPEND,
    UPSERT,
    RETRACT
}
