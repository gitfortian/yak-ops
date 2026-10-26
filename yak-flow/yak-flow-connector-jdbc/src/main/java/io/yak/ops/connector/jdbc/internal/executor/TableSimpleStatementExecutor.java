package io.yak.ops.connector.jdbc.internal.executor;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.core.data.RowData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Binds canonical RowData to a single PreparedStatement.
 *
 * <p>No Java-side record buffer is retained; the buffered executor owns pending records
 * and this class holds only the driver's statement batch during a flush.
 */
public final class TableSimpleStatementExecutor implements JdbcBatchStatementExecutor<RowData> {

    private static final Logger LOG = LoggerFactory.getLogger(TableSimpleStatementExecutor.class);

    private final String sql;
    private final JdbcDialectConverter converter;
    private final boolean exactOneRow;
    private volatile PreparedStatement statement;
    private int pendingRows;

    public TableSimpleStatementExecutor(String sql, JdbcDialectConverter converter) {
        this(sql, converter, false);
    }

    /** Fails closed when a single-row RETRACT cannot verify exactly one affected row. */
    public TableSimpleStatementExecutor(String sql, JdbcDialectConverter converter, boolean exactOneRow) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.exactOneRow = exactOneRow;
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        if (statement != null) {
            throw new IllegalStateException("JDBC statement is already prepared");
        }
        statement = Objects.requireNonNull(connection, "connection").prepareStatement(sql);
    }

    @Override
    public void addToBatch(RowData record) throws SQLException {
        PreparedStatement prepared = requireStatement();
        converter.toExternal(record, prepared);
        prepared.addBatch();
        pendingRows++;
    }

    @Override
    public void executeBatch() throws SQLException {
        long startedNanos = System.nanoTime();
        int rows = pendingRows;
        int[] affected = requireStatement().executeBatch();
        if (exactOneRow) {
            if (affected.length != rows) {
                throw new SQLException("JDBC RETRACT returned an incomplete batch update count");
            }
            for (int count : affected) {
                if (count != 1) {
                    // SUCCESS_NO_INFO is not sufficient proof for exact-row deletion.
                    throw new SQLException("JDBC RETRACT must delete exactly one physical row per event");
                }
            }
        }
        pendingRows = 0;
        if ("true".equals(MDC.get("taskLogDebug"))) {
            LOG.debug(
                    "JDBC statement batch executed, sql={}, statementRows={}, executeMs={}",
                    sql,
                    rows,
                    (System.nanoTime() - startedNanos) / 1_000_000);
        }
    }

    @Override
    public void closeStatements() throws SQLException {
        PreparedStatement prepared = statement;
        statement = null;
        pendingRows = 0;
        if (prepared != null) {
            prepared.close();
        }
    }

    @Override
    public void cancel() throws SQLException {
        PreparedStatement prepared = statement;
        if (prepared != null) {
            prepared.cancel();
        }
    }

    private PreparedStatement requireStatement() {
        PreparedStatement prepared = statement;
        if (prepared == null) {
            throw new IllegalStateException("JDBC statement is not prepared");
        }
        return prepared;
    }
}
