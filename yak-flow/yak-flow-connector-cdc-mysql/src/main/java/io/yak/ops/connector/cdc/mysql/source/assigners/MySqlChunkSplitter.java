package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.source.utils.ObjectUtils;
import io.yak.ops.connector.cdc.mysql.source.utils.StatementUtils;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans disjoint, half-open snapshot chunks using typed, database-ordered keyset boundaries.
 *
 * <p>Keyset boundaries accommodate sparse keys and avoid scanning huge empty ranges. The total range is always open on both ends, so inserts
 * outside the initially observed key span remain covered by the post-snapshot Binlog replay.
 * Planning runs on the coordinator's background discovery thread.
 */
public final class MySqlChunkSplitter {

    private static final int MAX_CHUNKS_PER_TABLE = 10_000;

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final Map<TableId, TableSchema> schemas;
    private MySqlSourceConfig sourceConfig;
    private final Map<TableId, String> chunkKeyColumns;
    private final int chunkSize;
    private final double distributionFactorLower;
    private final double distributionFactorUpper;
    private final Map<TableId, ChunkDistribution> tableDistributions = new ConcurrentHashMap<>();
    private volatile PreparedStatement planningStatement;
    private volatile boolean cancelled;

    public MySqlChunkSplitter(MySqlSourceConfig config, int chunkSize) {
        this(
                config::openSnapshotConnection,
                JdbcDialects.forUrl("jdbc:mysql:"),
                config.schemas(),
                config.chunkKeyColumns(),
                chunkSize,
                config.getDistributionFactorLower(),
                config.getDistributionFactorUpper());
        this.sourceConfig = Objects.requireNonNull(config, "config");
    }

    public MySqlChunkSplitter(
            JdbcConnectionProvider connections, JdbcDialect dialect, Map<TableId, TableSchema> schemas, int chunkSize) {
        this(connections, dialect, schemas, Map.of(), chunkSize);
    }

    public MySqlChunkSplitter(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Map<TableId, TableSchema> schemas,
            Map<TableId, String> chunkKeyColumns,
            int chunkSize) {
        this(connections, dialect, schemas, chunkKeyColumns, chunkSize, -1, -1);
    }

    MySqlChunkSplitter(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            Map<TableId, TableSchema> schemas,
            Map<TableId, String> chunkKeyColumns,
            int chunkSize,
            double distributionFactorLower,
            double distributionFactorUpper) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.schemas = new ConcurrentHashMap<>(Objects.requireNonNull(schemas, "schemas"));
        this.chunkKeyColumns = new ConcurrentHashMap<>(Objects.requireNonNull(chunkKeyColumns, "chunkKeyColumns"));
        this.sourceConfig = null;
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("MySQL snapshot chunk size must be between 1 and 100000");
        }
        this.chunkSize = chunkSize;
        this.distributionFactorLower = distributionFactorLower;
        this.distributionFactorUpper = distributionFactorUpper;
    }

    /**
     * Discovers tables matching Debezium's effective database/table filters. Newly discovered
     * table schemas and chunk keys are registered before any split for those tables is planned.
     */
    public List<TableId> discoverTables() throws SQLException {
        if (sourceConfig == null) throw new IllegalStateException("Runtime table discovery requires MySqlSourceConfig");
        List<TableId> configured = List.copyOf(sourceConfig.schemas().keySet());
        LinkedHashSet<TableId> discovered = new LinkedHashSet<>();
        try (Connection connection = connections.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            var filter = sourceConfig.getTableFilter();
            try (ResultSet result = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (result.next()) {
                    String catalog = result.getString("TABLE_CAT");
                    if (catalog == null) catalog = connection.getCatalog();
                    String schema = result.getString("TABLE_SCHEM");
                    String name = result.getString("TABLE_NAME");
                    if (catalog == null || name == null) continue;
                    io.debezium.relational.TableId debeziumId =
                            new io.debezium.relational.TableId(catalog, schema, name);
                    if (!filter.test(debeziumId)) continue;
                    TableId table = new TableId(catalog, schema, name);
                    TableSchema tableSchema =
                            sourceConfig.discoverTable(connection, table, sourceConfig.chunkKeyColumns().get(table));
                    schemas.put(table, tableSchema);
                    chunkKeyColumns.put(table, sourceConfig.chunkKeyColumns().get(table));
                    discovered.add(table);
                }
            }
        }
        if (discovered.isEmpty()) {
            throw new SQLException("No MySQL tables match the configured database/table filters");
        }
        List<TableId> ordered = new ArrayList<>();
        for (TableId table : configured) {
            if (discovered.remove(table)) ordered.add(table);
        }
        discovered.stream()
                .sorted(Comparator.comparing((TableId table) -> Objects.toString(table.catalog(), ""))
                        .thenComparing(table -> Objects.toString(table.schema(), ""))
                        .thenComparing(TableId::table))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    /**
     * Plans all snapshot ranges with the same database comparisons used by the active Source.
     */
    public List<ChunkRange> splitChunks(TableId table) throws SQLException {
        List<ChunkRange> ranges = new ArrayList<>();
        Object lower = null;
        while (true) {
            if (ranges.size() >= MAX_CHUNKS_PER_TABLE) {
                throw new IllegalStateException("MySQL snapshot exceeds the maximum chunk count");
            }
            ChunkRange range = nextChunk(table, lower);
            ranges.add(range);
            if (range.last()) return List.copyOf(ranges);
            lower = range.end();
        }
    }

    /**
     * Discovers one typed chunk from an optional database-ordered lower boundary.
     * Equal keys stay together and the next boundary is strictly greater in MySQL,
     * even when Java string comparisons disagree with the source collation.
     */
    public ChunkRange nextChunk(TableId table, Object lower) throws SQLException {
        if (cancelled) throw new SQLException("MySQL snapshot chunk discovery was cancelled");
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        Column column = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
        LogicalTypeRoot type = column.dataType().getTypeRoot();
        // Validate scalar family and caller-supplied resume boundary before opening JDBC.
        new ChunkRange(table, column.name(), type, lower, null);
        try (Connection connection = connections.getConnection()) {
            ChunkDistribution distribution = distributionFor(connection, table, column.name(), type);
            if (distribution != null) {
                if (lower == null && distribution.approximateRowCount <= chunkSize) {
                    return new ChunkRange(table, column.name(), type, null, null);
                }
                Object start = lower == null ? distribution.minimum : lower;
                if (start != null && distribution.dynamicChunkSize > 0) {
                    try {
                        Object next = ObjectUtils.plus(start, distribution.dynamicChunkSize);
                        if (ObjectUtils.compare(next, distribution.maximum) <= 0) {
                            return new ChunkRange(table, column.name(), type, lower, next);
                        }
                        return new ChunkRange(table, column.name(), type, lower, null);
                    } catch (ArithmeticException overflow) {
                        // Continue with Flink CDC's keyset query when the numeric bound overflows.
                    }
                }
            }
            Object maximum =
                    StatementUtils.queryNextChunkMax(connection, dialect, table, column.name(), type, chunkSize, lower);
            Object upper = maximum == null
                    ? null
                    : StatementUtils.queryMin(connection, dialect, table, column.name(), type, maximum);
            return new ChunkRange(table, column.name(), type, lower, upper);
        }
    }

    /**
     * Returns the effective chunk distribution using Flink CDC's evenly-sized split heuristic.
     * Non-numeric columns always retain the database-ordered keyset algorithm.
     */
    private ChunkDistribution distributionFor(Connection connection, TableId table, String column, LogicalTypeRoot type)
            throws SQLException {
        if (distributionFactorLower <= 0
                || distributionFactorUpper < distributionFactorLower
                || (type != LogicalTypeRoot.TINYINT
                        && type != LogicalTypeRoot.SMALLINT
                        && type != LogicalTypeRoot.INTEGER
                        && type != LogicalTypeRoot.BIGINT
                        && type != LogicalTypeRoot.DECIMAL)) {
            return null;
        }
        ChunkDistribution cached = tableDistributions.get(table);
        if (cached != null) return cached;
        Object[] minMax = StatementUtils.queryMinMax(connection, dialect, table, column, type);
        long rowCount = StatementUtils.queryApproximateRowCnt(connection, dialect, table);
        int dynamicChunkSize = getDynamicChunkSize(
                table, minMax[0], minMax[1], rowCount, chunkSize, distributionFactorLower, distributionFactorUpper);
        ChunkDistribution value = new ChunkDistribution(minMax[0], minMax[1], rowCount, dynamicChunkSize);
        tableDistributions.put(table, value);
        return value;
    }

    /**
     * Port of Flink CDC MySqlChunkSplitter.getDynamicChunkSize. A factor outside
     * the configured interval means the table must use uneven keyset chunks.
     */
    static int getDynamicChunkSize(
            TableId table,
            Object min,
            Object max,
            long approximateRowCnt,
            int chunkSize,
            double distributionFactorLower,
            double distributionFactorUpper) {
        if (min == null || max == null || approximateRowCnt <= 0) return -1;
        double factor = calculateDistributionFactor(table, min, max, approximateRowCnt);
        if (ObjectUtils.doubleCompare(factor, distributionFactorLower) >= 0
                && ObjectUtils.doubleCompare(factor, distributionFactorUpper) <= 0) {
            return Math.max((int) (factor * chunkSize), 1);
        }
        return -1;
    }

    /** Flink CDC's numeric distribution factor: (max - min + 1) / approximate row count. */
    static double calculateDistributionFactor(TableId table, Object min, Object max, long approximateRowCnt) {
        if (approximateRowCnt <= 0) return Double.MAX_VALUE;
        BigDecimal difference = ObjectUtils.minus(max, min);
        return difference
                .add(BigDecimal.ONE)
                .divide(BigDecimal.valueOf(approximateRowCnt), 4, RoundingMode.CEILING)
                .doubleValue();
    }

    private static final class ChunkDistribution {
        private final Object minimum;
        private final Object maximum;
        private final long approximateRowCount;
        private final int dynamicChunkSize;

        private ChunkDistribution(Object minimum, Object maximum, long approximateRowCount, int dynamicChunkSize) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.approximateRowCount = approximateRowCount;
            this.dynamicChunkSize = dynamicChunkSize;
        }
    }

    /**
     * Plans a chunk using the Flink CDC ChunkSplitterState that was recovered by the
     * enumerator. The typed boundary is reconstructed only after validating its table.
     */
    public MySqlSnapshotSplit planNext(TableId table, int tableIndex, ChunkSplitterState state) throws SQLException {
        Objects.requireNonNull(state, "chunk splitter state");
        TableId current = state.getCurrentSplittingTableId();
        if (current == null) {
            if (state.getNextChunkStart() != null || state.getNextChunkId() != null) {
                throw new IllegalArgumentException("Incomplete Flink chunk splitter checkpoint state");
            }
            return planNext(table, tableIndex, 0, null);
        }
        if (!current.equals(table)
                || state.getNextChunkId() == null
                || state.getNextChunkId() <= 0
                || state.getNextChunkStart() == null
                || state.getNextChunkStart().getBoundType() != ChunkSplitterState.ChunkBoundType.MIDDLE
                || state.getNextChunkStart().getValue() == null) {
            throw new IllegalArgumentException("Restored Flink chunk splitter state differs from its table");
        }
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        Column column = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
        Object nextStart = state.getNextChunkStart().getValue();
        ChunkRange cursor =
                new ChunkRange(table, column.name(), column.dataType().getTypeRoot(), nextStart, null);
        return planNext(table, tableIndex, state.getNextChunkId(), cursor);
    }

    /**
     * Plans the next Snapshot Split from the durable Enumerator discovery cursor.
     *
     * <p>The cursor identifies the next lower boundary, not a row identity or Reader
     * progress position. Non-unique key values stay within one half-open chunk.
     */
    public MySqlSnapshotSplit planNext(TableId table, int tableIndex, int chunkIndex, ChunkRange cursor)
            throws SQLException {
        if (cancelled || tableIndex < 0 || chunkIndex < 0 || chunkIndex >= MAX_CHUNKS_PER_TABLE) {
            throw new SQLException("MySQL typed chunk discovery is cancelled or exceeds its range limit");
        }
        TableSchema schema = Objects.requireNonNull(schemas.get(table), "table schema");
        Column column = ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
        if (cursor != null
                && (!cursor.tableId().equals(table)
                        || !cursor.column().equals(column.name())
                        || cursor.type() != column.dataType().getTypeRoot()
                        || cursor.start() == null
                        || cursor.end() != null)) {
            throw new IllegalArgumentException("Restored typed chunk cursor differs from the selected source column");
        }
        if ((chunkIndex == 0) != (cursor == null)) {
            throw new IllegalArgumentException("Typed chunk index and restored discovery cursor differ");
        }
        ChunkRange range = nextChunk(table, cursor == null ? null : cursor.start());
        return MySqlSnapshotSplit.typed("mysql-snapshot-" + tableIndex + "-" + chunkIndex, range);
    }

    /** Signals terminal discovery cancellation without blocking the coordinator. */
    public void cancel() {
        cancelled = true;
        PreparedStatement running = planningStatement;
        if (running != null)
            Thread.ofVirtual().name("yak-mysql-discovery-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    /* Discovery owns statement cleanup and observes cancellation. */
                }
            });
    }
}
