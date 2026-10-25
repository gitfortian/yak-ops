package io.yak.ops.connector.jdbc.internal.executor;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transactional JDBC receipts for keyless changes.
 *
 * <p>The caller owns the JDBC connection, transaction and commit. A unique receipt is inserted
 * before any row mutation; a duplicate receipt suppresses the entire mutation. Receipts are not
 * a replacement for source event identity and must never be deleted during checkpoint recovery.
 */
public final class JdbcReplayReceipts implements AutoCloseable {
    public static final String TABLE_NAME = "yak_flow_cdc_receipts";

    private final Map<TableId, PreparedStatement> statements = new LinkedHashMap<>();

    public JdbcReplayReceipts(Connection connection, JdbcDialect dialect, List<JdbcTableWritePlan> plans)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(dialect, "dialect");
        try {
            for (JdbcTableWritePlan plan : plans) {
                if (plan.writeMode() != JdbcWriteMode.RETRACT) continue;
                TableId table = plan.targetTable();
                TableId receiptTable = new TableId(
                        table.catalog() == null ? connection.getCatalog() : table.catalog(),
                        table.schema() == null ? connection.getSchema() : table.schema(),
                        dialect.replayReceiptTableName());
                String sql = dialect.replayReceiptSql(receiptTable);
                statements.put(plan.sourceTable(), connection.prepareStatement(sql));
            }
        } catch (SQLException | RuntimeException failure) {
            try {
                close();
            } catch (SQLException error) {
                failure.addSuppressed(error);
            }
            throw failure;
        }
    }

    /**
     * Reserve exactly one source mutation. The INSERT and the caller's DML must commit together.
     * A rollback discards both; a committed receipt makes future replays no-ops.
     */
    public boolean reserve(TableRecord record, JdbcTableWritePlan plan) throws SQLException {
        ChangeEventId id = Objects.requireNonNull(record.changeEventId(), "CDC RETRACT requires a stable event ID");
        PreparedStatement statement = statements.get(record.tableId());
        if (statement == null || plan.writeMode() != JdbcWriteMode.RETRACT) {
            throw new IllegalStateException("No transactional receipt statement for JDBC RETRACT");
        }
        statement.setBytes(1, receiptKey(id, plan));
        int count = statement.executeUpdate();
        if (count != 0 && count != 1) {
            throw new SQLException("JDBC RETRACT receipt returned an unexpected affected-row count");
        }
        return count == 1;
    }

    /** Deterministic, domain-separated, length-prefixed hash; neither values nor secrets are logged. */
    public static byte[] receiptKey(ChangeEventId id, JdbcTableWritePlan plan) {
        Objects.requireNonNull(id, "CDC event identity");
        Objects.requireNonNull(plan, "write plan");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, "yak-flow-mysql-cdc-retract-v1");
            append(digest, id.sourceId());
            appendTable(digest, plan.sourceTable());
            appendTable(digest, plan.targetTable());
            append(digest, id.eventId());
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendTable(MessageDigest digest, TableId table) {
        append(digest, table.catalog());
        append(digest, table.schema());
        append(digest, table.table());
    }

    private static void append(MessageDigest digest, String text) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    @Override
    public void close() throws SQLException {
        SQLException failure = null;
        for (PreparedStatement statement : statements.values()) {
            try {
                statement.close();
            } catch (SQLException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        statements.clear();
        if (failure != null) throw failure;
    }

    /** Statement.cancel is used by the mailbox owner during task cancellation. */
    public void cancel() throws SQLException {
        SQLException failure = null;
        for (PreparedStatement statement : statements.values()) {
            try {
                statement.cancel();
            } catch (SQLException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
