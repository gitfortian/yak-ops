package io.yak.ops.flow.connector.jdbc;

/**
 * JDBC Sink 当前支持的写入语义。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum JdbcWriteMode {

    /** 离线批量写入，只接受 INSERT RowKind 并使用 JDBC executeBatch。 */
    INSERT,

    /** CDC changelog 写入，按主键顺序应用 INSERT/UPDATE/DELETE 并按批次提交事务。 */
    CHANGELOG
}
