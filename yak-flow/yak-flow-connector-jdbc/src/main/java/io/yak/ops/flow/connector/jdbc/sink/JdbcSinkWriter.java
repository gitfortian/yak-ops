package io.yak.ops.flow.connector.jdbc.sink;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.SinkWriter;
import io.yak.ops.flow.connector.jdbc.JdbcSaveMode;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Sink Writer：离线模式保留 PreparedStatement batch，CDC 模式按主键顺序应用 changelog 并按批次提交事务。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class JdbcSinkWriter implements SinkWriter {

    private final JdbcSinkConfig config;
    private final YakTableSchema schema;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcDialect dialect;
    private final List<Integer> primaryKeyIndexes = new ArrayList<>();

    private Connection connection;
    private PreparedStatement insertStatement;
    private PreparedStatement deleteStatement;
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
        for (String primaryKey : schema.primaryKeys()) {
            int index = -1;
            for (int i = 0; i < schema.columnCount(); i++) {
                if (schema.column(i).name().equals(primaryKey)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("primary key column not found in schema: " + primaryKey);
            }
            primaryKeyIndexes.add(index);
        }
    }

    @Override
    public void open() throws Exception {
        if (config.writeMode() == JdbcWriteMode.CHANGELOG && primaryKeyIndexes.isEmpty()) {
            throw new IllegalArgumentException("JDBC CHANGELOG Sink requires primary key");
        }

        connection = connectionProvider.open(config.connection(), config.timeoutSeconds());
        connection.setAutoCommit(false);
        applySaveMode();
        insertStatement = connection.prepareStatement(dialect.insertSql(config.table(), schema));
        insertStatement.setQueryTimeout(config.timeoutSeconds());
        if (config.writeMode() == JdbcWriteMode.CHANGELOG) {
            deleteStatement = connection.prepareStatement(dialect.deleteSql(config.table(), schema));
            deleteStatement.setQueryTimeout(config.timeoutSeconds());
        }
    }

    @Override
    public void write(List<YakRow> rows) throws Exception {
        try {
            if (config.writeMode() == JdbcWriteMode.INSERT) {
                writeInsertBatch(rows);
                return;
            }
            writeChangelog(rows);
        } catch (Exception exception) {
            rollbackQuietly();
            throw exception;
        }
    }

    @Override
    public void flush() throws Exception {
        try {
            if (config.writeMode() == JdbcWriteMode.INSERT) {
                executeInsertBatch();
                return;
            }
            commitChangelog();
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
            if (insertStatement != null) insertStatement.close();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
        }
        try {
            if (deleteStatement != null) deleteStatement.close();
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

    private void applySaveMode() throws Exception {
        if (config.saveMode() == JdbcSaveMode.APPEND) return;
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(config.timeoutSeconds());
            statement.execute(dialect.truncateSql(config.table()));
            connection.commit();
        } catch (Exception exception) {
            rollbackQuietly();
            throw exception;
        }
    }

    private void writeInsertBatch(List<YakRow> rows) throws Exception {
        for (YakRow row : rows) {
            if (row.rowKind() != RowKind.INSERT) {
                throw new IllegalArgumentException("JDBC INSERT Sink only accepts INSERT RowKind");
            }
            validateArity(row);
            bindFullRow(insertStatement, row);
            insertStatement.addBatch();
            pendingRows++;
            if (pendingRows >= config.batchSize()) {
                executeInsertBatch();
            }
        }
    }

    private void writeChangelog(List<YakRow> rows) throws Exception {
        for (YakRow row : rows) {
            validateArity(row);
            switch (row.rowKind()) {
                case INSERT, UPDATE_AFTER -> replaceByPrimaryKey(row);
                case UPDATE_BEFORE, DELETE -> deleteByPrimaryKey(row);
            }
            pendingRows++;
            if (pendingRows >= config.batchSize()) {
                commitChangelog();
            }
        }
    }

    private void replaceByPrimaryKey(YakRow row) throws Exception {
        deleteByPrimaryKey(row);
        bindFullRow(insertStatement, row);
        insertStatement.executeUpdate();
    }

    private void deleteByPrimaryKey(YakRow row) throws Exception {
        for (int parameterIndex = 0; parameterIndex < primaryKeyIndexes.size(); parameterIndex++) {
            int rowIndex = primaryKeyIndexes.get(parameterIndex);
            bind(deleteStatement, parameterIndex + 1, schema.column(rowIndex).dataType(), row.value(rowIndex));
        }
        deleteStatement.executeUpdate();
    }

    private void executeInsertBatch() throws Exception {
        if (pendingRows == 0) return;
        insertStatement.executeBatch();
        connection.commit();
        pendingRows = 0;
    }

    private void commitChangelog() throws Exception {
        if (pendingRows == 0) return;
        connection.commit();
        pendingRows = 0;
    }

    private void validateArity(YakRow row) {
        if (row.arity() != schema.columnCount()) {
            throw new IllegalArgumentException("YakRow field count does not match target schema");
        }
    }

    private void bindFullRow(PreparedStatement statement, YakRow row) throws Exception {
        for (int index = 0; index < schema.columnCount(); index++) {
            bind(statement, index + 1, schema.column(index).dataType(), row.value(index));
        }
    }

    private void bind(PreparedStatement statement, int parameterIndex, YakDataType dataType, Object value)
            throws Exception {
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
        return switch (dataType.kind()) {
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
        pendingRows = 0;
    }
}
