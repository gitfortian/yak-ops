package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.source.SourceSplit;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.Objects;

/**
 * bounded JDBC 表读取分片；Phase 3 一个表只产生一个 split。
 *
 * @param table 当前分片读取的源表
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSourceSplit(DataSourceTablePath table) implements SourceSplit {

    public JdbcSourceSplit {
        Objects.requireNonNull(table, "table must not be null");
    }

    @Override
    public String splitId() {
        return String.join(
                ".",
                table.database() == null ? "" : table.database(),
                table.schema() == null ? "" : table.schema(),
                table.table());
    }
}
