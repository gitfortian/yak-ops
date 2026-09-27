package io.yak.ops.flow.connector.jdbc.sink;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.SinkWriter;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 使用 PreparedStatement batch 把 INSERT YakRow 写入 JDBC 目标表。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSinkWriter implements SinkWriter {

    private final JdbcSinkConfig config;
    private final YakTableSchema schema;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;

    private Connection connection;
    private PreparedStatement statement;
    private int pendingRows;

    JdbcSinkWriter(
            JdbcSinkConfig config,
            YakTableSchema schema,
            JdbcConnectionProvider connectionProvider,
            JdbcDialect dialect) {
        this.config = config;
        this.schema = schema;
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
    }

    @Override
    public void open() throws Exception {
        connection = connectionProvider.open(config.connection(), config.timeoutSeconds());
        connection.setAutoCommit(false);
        statement = connection.prepareStatement(dialect.insertSql(config.table(), schema));
        statement.setQueryTimeout(config.timeoutSeconds());
    }

    @Override
    public void write(List<YakRow> rows) throws Exception {
        try {
            for (YakRow row : rows) {
                if (row.rowKind() != RowKind.INSERT) {
                    throw new IllegalArgumentException("JDBC Batch Sink 当前只接受 INSERT RowKind");
                }
                if (row.arity() != schema.columnCount()) {
                    throw new IllegalArgumentException("YakRow 字段数量与目标 Schema 不一致");
                }

                for (int index = 0; index < schema.columnCount(); index++) {
                    bind(index + 1, schema.column(index).dataType(), row.value(index));
                }
                statement.addBatch();
                pendingRows++;
                if (pendingRows >= config.batchSize()) {
                    executePending();
                }
            }
        } catch (Exception exception) {
            rollbackQuietly();
            throw exception;
        }
    }

    @Override
    public void flush() throws Exception {
        try {
            executePending();
        } catch (Exception exception) {
            rollbackQuietly();
            throw exception;
        }
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        if (connection != null && pendingRows > 0) {
            try {
                connection.rollback();
            } catch (Exception exception) {
                failure = exception;
            }
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

    private void executePending() throws Exception {
        if (pendingRows == 0) return;
        statement.executeBatch();
        connection.commit();
        pendingRows = 0;
    }

    private void bind(int parameterIndex, YakDataType dataType, Object value) throws Exception {
        if (value == null) {
            statement.setNull(parameterIndex, sqlType(dataType));
            return;
        }
        if (value instanceof byte[] bytes) {
            statement.setBytes(parameterIndex, bytes);
            return;
        }
        if (value instanceof LocalDate
                || value instanceof LocalTime
                || value instanceof LocalDateTime
                || value instanceof OffsetDateTime) {
            statement.setObject(parameterIndex, value);
            return;
        }
        statement.setObject(parameterIndex, value);
    }

    private int sqlType(YakDataType dataType) {
        return switch (dataType) {
            case BOOLEAN -> Types.BOOLEAN;
            case TINYINT -> Types.TINYINT;
            case SMALLINT -> Types.SMALLINT;
            case INTEGER -> Types.INTEGER;
            case BIGINT -> Types.BIGINT;
            case FLOAT -> Types.FLOAT;
            case DOUBLE -> Types.DOUBLE;
            case DECIMAL -> Types.DECIMAL;
            case STRING -> Types.VARCHAR;
            case BINARY -> Types.VARBINARY;
            case DATE -> Types.DATE;
            case TIME -> Types.TIME;
            case TIMESTAMP -> Types.TIMESTAMP;
            case TIMESTAMP_WITH_TIME_ZONE -> Types.TIMESTAMP_WITH_TIMEZONE;
        };
    }

    private void rollbackQuietly() {
        if (connection == null) return;
        try {
            connection.rollback();
        } catch (Exception ignored) {
            // 保留原始写入异常，rollback 失败由上层连接关闭处理。
        }
    }
}
