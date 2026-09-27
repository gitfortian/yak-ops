package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.Optional;

/**
 * Phase 3 JDBC 单表 Source 的 bounded split 枚举器。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceSplitEnumerator implements SourceSplitEnumerator<JdbcSourceSplit> {

    private final DataSourceTablePath table;
    private boolean assigned;

    JdbcSourceSplitEnumerator(DataSourceTablePath table) {
        this.table = table;
    }

    @Override
    public Optional<JdbcSourceSplit> nextSplit() {
        if (assigned) {
            return Optional.empty();
        }
        assigned = true;
        return Optional.of(new JdbcSourceSplit(table));
    }

    @Override
    public boolean isFinished() {
        return assigned;
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        return new JdbcEnumeratorState(assigned);
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof JdbcEnumeratorState jdbcState)) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint state 类型不匹配");
        }
        assigned = jdbcState.assigned();
    }
}
