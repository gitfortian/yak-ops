package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;

/**
 * Restricts automatic DDL retries to confirmed transient failures.
 *
 * <p>A lost or timed-out DDL response is not a confirmed failure. JdbcMetadataApplier fences those
 * outcomes rather than submitting the statement again while the server may still be executing it.
 */
final class JdbcDdlRetryPolicy {

    static final int MAX_RETRIES = 3;

    private JdbcDdlRetryPolicy() {}

    /**
     * SQLSTATE 08 failures observed while reading metadata are safe to retry. Failures with this
     * state during statement execution are marked outcome-unknown before reaching this policy.
     */
    static boolean isRetryable(SQLException failure, JdbcDialect dialect) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            if (error instanceof SQLTimeoutException || error instanceof SQLRecoverableException) {
                return false;
            }
            String state = error.getSQLState();
            if ("40001".equals(state)
                    || "40P01".equals(state)
                    || "55P03".equals(state)
                    || (state != null && state.startsWith("08"))) {
                return true;
            }
            // MySQL deadlock and metadata-lock wait timeout are server-confirmed errors.
            if (dialect instanceof MySqlJdbcDialect
                    && (error.getErrorCode() == 1205 || error.getErrorCode() == 1213)) {
                return true;
            }
        }
        return false;
    }

    static void pauseBeforeRetry(int retryNumber) throws InterruptedException {
        // Retry at most three times: 100 ms, 200 ms, 400 ms. No unbounded retry loops.
        Thread.sleep(100L << (retryNumber - 1));
    }
}
