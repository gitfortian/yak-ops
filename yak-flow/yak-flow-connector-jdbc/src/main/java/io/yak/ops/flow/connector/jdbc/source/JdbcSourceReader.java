package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.source.SourceReader;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 forward-only JDBC cursor 分批读取一个表 split，并按 YakTableSchema 顺序产出 YakRow。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSourceReader implements SourceReader<JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;

    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private boolean finished;
    private long rowsRead;

    JdbcSourceReader(JdbcSourceConfig config, JdbcConnectionProvider connectionProvider, JdbcDialect dialect) {
        this.config = config;
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
    }

    @Override
    public void open(JdbcSourceSplit split) throws Exception {
        if (!config.table().equals(split.table())) {
            throw new IllegalArgumentException("JDBC Source split 与配置表不匹配");
        }

        connection = connectionProvider.open(config.connection(), config.timeoutSeconds());
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        if (connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ)) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        }

        statement = connection.prepareStatement(
                dialect.selectSql(split.table(), config.schema()),
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(config.fetchSize());
        statement.setQueryTimeout(config.timeoutSeconds());
        resultSet = statement.executeQuery();
    }

    @Override
    public List<YakRow> poll() throws Exception {
        if (finished) {
            return List.of();
        }

        List<YakRow> rows = new ArrayList<>(config.readBatchSize());
        while (rows.size() < config.readBatchSize()) {
            if (!resultSet.next()) {
                finished = true;
                break;
            }
            List<Object> values = new ArrayList<>(config.schema().columnCount());
            for (int index = 1; index <= config.schema().columnCount(); index++) {
                values.add(normalizeValue(resultSet.getObject(index)));
            }
            rows.add(new YakRow(io.yak.ops.flow.api.row.RowKind.INSERT, values));
            rowsRead++;
        }
        return rows;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        return new JdbcReaderState(rowsRead);
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof JdbcReaderState jdbcState)) {
            throw new IllegalArgumentException("JDBC Reader checkpoint state 类型不匹配");
        }
        if (jdbcState.rowsRead() != 0) {
            throw new UnsupportedOperationException("当前 JDBC batch checkpoint 不支持跨执行恢复");
        }
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        try {
            if (resultSet != null) resultSet.close();
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            if (statement != null) statement.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        if (failure != null) throw failure;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }
}
