package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import org.junit.jupiter.api.Test;

class JdbcDdlRetryPolicyTest {

    @Test
    void retriesOnlyConfirmedTransientServerErrors() {
        var postgres = JdbcFactoryLoader.loadDialect("jdbc:postgresql:");
        var mysql = JdbcFactoryLoader.loadDialect("jdbc:mysql:");

        assertTrue(JdbcDdlRetryPolicy.isRetryable(new SQLException("deadlock", "40P01"), postgres));
        assertTrue(JdbcDdlRetryPolicy.isRetryable(new SQLException("serialization", "40001"), postgres));
        assertTrue(JdbcDdlRetryPolicy.isRetryable(new SQLException("lock unavailable", "55P03"), postgres));
        assertTrue(JdbcDdlRetryPolicy.isRetryable(new SQLException("mysql deadlock", "40001", 1213), mysql));
        assertTrue(JdbcDdlRetryPolicy.isRetryable(new SQLException("mysql lock wait", "HY000", 1205), mysql));
        assertFalse(JdbcDdlRetryPolicy.isRetryable(new SQLException("lock wait", "HY000", 1205), postgres));
        assertFalse(JdbcDdlRetryPolicy.isRetryable(new SQLException("permission denied", "42501"), postgres));
        assertFalse(JdbcDdlRetryPolicy.isRetryable(new SQLException("unsupported SQL", "42000"), mysql));
        assertFalse(JdbcDdlRetryPolicy.isRetryable(new SQLTimeoutException("timeout"), postgres));
        assertFalse(JdbcDdlRetryPolicy.isRetryable(new SQLRecoverableException("unknown"), mysql));
    }
}
