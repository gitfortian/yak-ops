package io.yak.ops.flow.connector.jdbc;

import java.util.Objects;

/**
 * 定义 bounded JDBC Source 的显式数值范围分片参数；V1 仅支持单整数主键。
 *
 * @param column 分片字段名称，必须与 Source Schema 中的单主键名称一致
 * @param lowerBound 分片字段最小包含值
 * @param upperBound 分片字段最大包含值
 * @param splitCount 期望生成的最大分片数量；值域小于该数量时不会生成空分片
 * @author weifuwan
 * @since 2026-09-28
 */
public record JdbcNumericSplitConfig(String column, long lowerBound, long upperBound, int splitCount) {

    public JdbcNumericSplitConfig {
        Objects.requireNonNull(column, "column must not be null");
        if (column.isBlank()) {
            throw new IllegalArgumentException("column must not be blank");
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("lowerBound must not be greater than upperBound");
        }
        if (splitCount <= 0) {
            throw new IllegalArgumentException("splitCount must be greater than 0");
        }
    }
}
