package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.flow.connector.jdbc.JdbcNumericSplitConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC bounded Source 的分片枚举器；无分片配置时返回整表 split，显式数值配置时生成互不重叠的整数范围。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceSplitEnumerator implements SourceSplitEnumerator<JdbcSourceSplit> {

    private static final BigInteger ONE = BigInteger.ONE;

    private final List<JdbcSourceSplit> splits;
    private int nextSplitIndex;

    JdbcSourceSplitEnumerator(JdbcSourceConfig config) {
        this.splits = createSplits(config);
    }

    @Override
    public Optional<JdbcSourceSplit> nextSplit() {
        if (isFinished()) return Optional.empty();
        return Optional.of(splits.get(nextSplitIndex++));
    }

    @Override
    public boolean isFinished() {
        return nextSplitIndex >= splits.size();
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        return new JdbcEnumeratorState(nextSplitIndex);
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof JdbcEnumeratorState jdbcState)) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint state 类型不匹配");
        }
        if (jdbcState.nextSplitIndex() < 0 || jdbcState.nextSplitIndex() > splits.size()) {
            throw new IllegalArgumentException("JDBC Enumerator checkpoint split index 超出范围");
        }
        nextSplitIndex = jdbcState.nextSplitIndex();
    }

    private List<JdbcSourceSplit> createSplits(JdbcSourceConfig config) {
        JdbcNumericSplitConfig splitConfig = config.splitConfig();
        if (splitConfig == null) {
            return List.of(new JdbcSourceSplit(config.table()));
        }

        BigInteger lowerBound = BigInteger.valueOf(splitConfig.lowerBound());
        BigInteger upperBound = BigInteger.valueOf(splitConfig.upperBound());
        BigInteger valueCount = upperBound.subtract(lowerBound).add(ONE);
        BigInteger requestedSplitCount = BigInteger.valueOf(splitConfig.splitCount());
        BigInteger splitSize = valueCount.add(requestedSplitCount).subtract(ONE).divide(requestedSplitCount);

        List<JdbcSourceSplit> result = new ArrayList<>();
        BigInteger currentLowerBound = lowerBound;
        while (currentLowerBound.compareTo(upperBound) <= 0) {
            BigInteger currentUpperBound = currentLowerBound.add(splitSize).subtract(ONE).min(upperBound);
            result.add(new JdbcSourceSplit(
                    config.table(),
                    splitConfig.column(),
                    currentLowerBound.longValueExact(),
                    currentUpperBound.longValueExact()));
            currentLowerBound = currentUpperBound.add(ONE);
        }
        return List.copyOf(result);
    }
}
