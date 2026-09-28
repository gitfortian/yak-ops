package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.flow.connector.jdbc.JdbcNumericSplitConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC bounded Source 的分片枚举器；可使用显式整数范围，也可按单整数主键的 MIN/MAX/rowCount 动态规划范围。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceSplitEnumerator implements SourceSplitEnumerator<JdbcSourceSplit> {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final int MAX_DYNAMIC_SPLIT_COUNT = 10_000;

    private final JdbcSourceConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private List<JdbcSourceSplit> splits;
    private int nextSplitIndex;

    JdbcSourceSplitEnumerator(
            JdbcSourceConfig config, JdbcConnectionProvider connectionProvider, JdbcDialect dialect) {
        this.config = config;
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
        if (config.splitConfig() != null) {
            splits = createSplits(config.splitConfig());
        } else if (config.splitSize() == null) {
            splits = wholeTableSplit();
        }
    }

    @Override
    public void start() throws Exception {
        if (splits != null) return;
        splits = createDynamicSplits();
    }

    @Override
    public Optional<JdbcSourceSplit> nextSplit() {
        requireStarted();
        if (isFinished()) return Optional.empty();
        return Optional.of(splits.get(nextSplitIndex++));
    }

    @Override
    public boolean isFinished() {
        requireStarted();
        return nextSplitIndex >= splits.size();
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        requireStarted();
        return new JdbcEnumeratorState(nextSplitIndex);
    }

    @Override
    public void restore(CheckpointState state) {
        requireStarted();
        if (!(state instanceof JdbcEnumeratorState jdbcState)) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint state 类型不匹配");
        }
        if (jdbcState.nextSplitIndex() < 0 || jdbcState.nextSplitIndex() > splits.size()) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint split index 超出范围");
        }
        nextSplitIndex = jdbcState.nextSplitIndex();
    }

    private List<JdbcSourceSplit> createDynamicSplits() throws Exception {
        Optional<String> splitColumn = JdbcNumericSplitConfig.eligibleColumn(config.schema());
        if (splitColumn.isEmpty()) return wholeTableSplit();

        try (Connection connection = connectionProvider.open(config.connection(), config.timeoutSeconds())) {
            connection.setReadOnly(true);
            try (var statement =
                    connection.prepareStatement(dialect.splitStatisticsSql(config.table(), splitColumn.get()))) {
                statement.setQueryTimeout(config.timeoutSeconds());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("JDBC split statistics query returned no row");
                }
                long rowCount = resultSet.getLong(3);
                if (rowCount <= config.splitSize()) return wholeTableSplit();

                long lowerBound = resultSet.getLong(1);
                if (resultSet.wasNull()) return wholeTableSplit();
                long upperBound = resultSet.getLong(2);
                if (resultSet.wasNull()) return wholeTableSplit();

                long splitCount = rowCount / config.splitSize();
                if (rowCount % config.splitSize() != 0) splitCount++;
                if (splitCount > MAX_DYNAMIC_SPLIT_COUNT) {
                    throw new IllegalArgumentException(
                            "dynamic JDBC split count exceeds "
                                    + MAX_DYNAMIC_SPLIT_COUNT
                                    + "; increase splitSize");
                }
                return createSplits(
                        new JdbcNumericSplitConfig(splitColumn.get(), lowerBound, upperBound, (int) splitCount));
            }
        }
    }

    private List<JdbcSourceSplit> createSplits(JdbcNumericSplitConfig splitConfig) {
        BigInteger lowerBound = BigInteger.valueOf(splitConfig.lowerBound());
        BigInteger upperBound = BigInteger.valueOf(splitConfig.upperBound());
        BigInteger valueCount = upperBound.subtract(lowerBound).add(ONE);
        BigInteger requestedSplitCount = BigInteger.valueOf(splitConfig.splitCount());
        BigInteger rangeSize = valueCount.add(requestedSplitCount).subtract(ONE).divide(requestedSplitCount);

        List<JdbcSourceSplit> result = new ArrayList<>();
        BigInteger currentLowerBound = lowerBound;
        while (currentLowerBound.compareTo(upperBound) <= 0) {
            BigInteger currentUpperBound = currentLowerBound.add(rangeSize).subtract(ONE).min(upperBound);
            result.add(new JdbcSourceSplit(
                    config.table(),
                    splitConfig.column(),
                    currentLowerBound.longValueExact(),
                    currentUpperBound.longValueExact()));
            currentLowerBound = currentUpperBound.add(ONE);
        }
        return List.copyOf(result);
    }

    private List<JdbcSourceSplit> wholeTableSplit() {
        return List.of(new JdbcSourceSplit(config.table()));
    }

    private void requireStarted() {
        if (splits == null) {
            throw new IllegalStateException("JDBC split enumerator must be started before use");
        }
    }
}
