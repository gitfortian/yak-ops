package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;

/**
 * bounded JDBC Source 的执行配置，直接引用 Datasource 已规范化连接和 Catalog 表路径。
 *
 * @param connection 已规范化的数据源连接
 * @param table 源表路径
 * @param schema 源表逻辑结构
 * @param fetchSize JDBC 游标 fetch size
 * @param readBatchSize 每次向 Runtime 输出的最大行数
 * @param timeoutSeconds 连接与查询超时秒数
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSourceConfig(
        DataSourceConnection connection,
        DataSourceTablePath table,
        YakTableSchema schema,
        int fetchSize,
        int readBatchSize,
        int timeoutSeconds) {

    private static final int DEFAULT_FETCH_SIZE = 500;
    private static final int DEFAULT_READ_BATCH_SIZE = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public JdbcSourceConfig {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (fetchSize <= 0) throw new IllegalArgumentException("fetchSize must be greater than 0");
        if (readBatchSize <= 0) throw new IllegalArgumentException("readBatchSize must be greater than 0");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
    }

    public static JdbcSourceConfig defaults(
            DataSourceConnection connection, DataSourceTablePath table, YakTableSchema schema) {
        return new JdbcSourceConfig(
                connection, table, schema, DEFAULT_FETCH_SIZE, DEFAULT_READ_BATCH_SIZE, DEFAULT_TIMEOUT_SECONDS);
    }
}
