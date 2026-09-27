package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;

/**
 * JDBC Sink 的批量写入配置；目标表必须在执行前存在。
 *
 * @param connection 已规范化的数据源连接
 * @param table 目标表路径
 * @param batchSize 每次 JDBC executeBatch 的最大行数
 * @param timeoutSeconds 连接与语句超时秒数
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSinkConfig(
        DataSourceConnection connection, DataSourceTablePath table, int batchSize, int timeoutSeconds) {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public JdbcSinkConfig {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be greater than 0");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
    }

    public static JdbcSinkConfig defaults(DataSourceConnection connection, DataSourceTablePath table) {
        return new JdbcSinkConfig(connection, table, DEFAULT_BATCH_SIZE, DEFAULT_TIMEOUT_SECONDS);
    }
}
