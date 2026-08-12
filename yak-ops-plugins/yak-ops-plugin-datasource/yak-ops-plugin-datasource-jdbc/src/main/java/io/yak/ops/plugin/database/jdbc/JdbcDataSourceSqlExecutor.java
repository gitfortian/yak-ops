package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** JDBC implementation of one cancellable SQL execution attempt. */
public final class JdbcDataSourceSqlExecutor implements DataSourceSqlExecutor {

  private static final int MAX_TEXT_CHARS = 65_536;
  private static final int MAX_INLINE_BINARY_BYTES = 4_096;

  private final JdbcConnectionProperties connection;
  private final int connectionTimeoutSeconds;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Connection> activeConnection = new AtomicReference<>();
  private final AtomicReference<Statement> activeStatement = new AtomicReference<>();

  public JdbcDataSourceSqlExecutor(
      JdbcConnectionProperties connection,
      int connectionTimeoutSeconds) {
    this.connection = connection;
    this.connectionTimeoutSeconds = Math.max(1, connectionTimeoutSeconds);
  }

  @Override
  public DataSourceSqlResult execute(DataSourceSqlRequest request) {
    if (cancelled.get()) {
      throw executionError("SQL 执行已取消", null);
    }

    try {
      Class.forName(connection.driverClassName());
      DriverManager.setLoginTimeout(connectionTimeoutSeconds);
      try (Connection opened =
          DriverManager.getConnection(connection.jdbcUrl(), connectionProperties())) {
        activeConnection.set(opened);
        if (cancelled.get()) {
          throw executionError("SQL 执行已取消", null);
        }

        try (Statement statement = opened.createStatement()) {
          activeStatement.set(statement);
          try {
            statement.setQueryTimeout(request.timeoutSeconds());
          } catch (SQLFeatureNotSupportedException ignored) {
            // Some JDBC drivers do not implement query timeout. Cancellation still remains available.
          }
          statement.setMaxRows(Math.min(Integer.MAX_VALUE - 1, request.maxRows() + 1));

          boolean hasResultSet = statement.execute(request.sql());
          if (!hasResultSet) {
            return DataSourceSqlResult.update(statement.getUpdateCount());
          }

          try (ResultSet resultSet = statement.getResultSet()) {
            return readResultSet(resultSet, request.maxRows());
          }
        } finally {
          activeStatement.set(null);
        }
      } finally {
        activeConnection.set(null);
      }
    } catch (DataSourcePluginException exception) {
      throw exception;
    } catch (ClassNotFoundException exception) {
      throw executionError("数据库驱动未安装：" + connection.driverClassName(), exception);
    } catch (Exception exception) {
      throw executionError(safeMessage(exception), exception);
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    Statement statement = activeStatement.get();
    if (statement != null) {
      try {
        statement.cancel();
      } catch (Exception ignored) {
        // Best effort; closing the connection below is the fallback.
      }
    }
    Connection opened = activeConnection.get();
    if (opened != null) {
      try {
        opened.close();
      } catch (Exception ignored) {
        // Best effort cancellation.
      }
    }
  }

  @Override
  public void close() {
    Statement statement = activeStatement.get();
    Connection opened = activeConnection.get();
    if (statement == null && opened == null) return;
    cancel();
  }

  private DataSourceSqlResult readResultSet(ResultSet resultSet, int maxRows) throws Exception {
    ResultSetMetaData metadata = resultSet.getMetaData();
    List<DataSourceSqlColumn> columns = columns(metadata);
    List<List<Object>> rows = new ArrayList<>();
    boolean truncated = false;

    while (resultSet.next()) {
      if (rows.size() >= maxRows) {
        truncated = true;
        break;
      }
      List<Object> row = new ArrayList<>(metadata.getColumnCount());
      for (int index = 1; index <= metadata.getColumnCount(); index++) {
        row.add(normalizeValue(resultSet.getObject(index)));
      }
      rows.add(row);
    }
    return DataSourceSqlResult.query(columns, rows, truncated);
  }

  private List<DataSourceSqlColumn> columns(ResultSetMetaData metadata) throws Exception {
    List<DataSourceSqlColumn> columns = new ArrayList<>(metadata.getColumnCount());
    for (int index = 1; index <= metadata.getColumnCount(); index++) {
      String name = metadata.getColumnName(index);
      String label = metadata.getColumnLabel(index);
      columns.add(
          new DataSourceSqlColumn(
              name,
              label == null || label.isBlank() ? name : label,
              metadata.getColumnTypeName(index),
              metadata.getColumnType(index),
              metadata.isNullable(index) != ResultSetMetaData.columnNoNulls));
    }
    return columns;
  }

  private Object normalizeValue(Object value) throws Exception {
    if (value == null
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character) {
      return value;
    }
    if (value instanceof String text) {
      return limitText(text);
    }
    if (value instanceof byte[] bytes) {
      if (bytes.length > MAX_INLINE_BINARY_BYTES) {
        return "<BINARY " + bytes.length + " bytes>";
      }
      return Base64.getEncoder().encodeToString(bytes);
    }
    if (value instanceof Blob blob) {
      return "<BLOB " + blob.length() + " bytes>";
    }
    if (value instanceof Clob clob) {
      long length = clob.length();
      int readLength = (int) Math.min(length, MAX_TEXT_CHARS);
      String text = clob.getSubString(1L, readLength);
      return length > MAX_TEXT_CHARS ? text + "…" : text;
    }
    if (value instanceof SQLXML xml) {
      return limitText(xml.getString());
    }
    if (value instanceof java.sql.Date
        || value instanceof java.sql.Time
        || value instanceof java.sql.Timestamp
        || value instanceof java.time.temporal.TemporalAccessor
        || value instanceof java.util.UUID) {
      return value.toString();
    }
    return limitText(String.valueOf(value));
  }

  private String limitText(String value) {
    if (value == null || value.length() <= MAX_TEXT_CHARS) return value;
    return value.substring(0, MAX_TEXT_CHARS) + "…";
  }

  private Properties connectionProperties() {
    Properties properties = new Properties();
    properties.putAll(connection.properties());
    if (connection.username() != null && !connection.username().isBlank()) {
      properties.setProperty("user", connection.username());
    }
    if (connection.password() != null) {
      properties.setProperty("password", connection.password());
    }
    return properties;
  }

  private DataSourcePluginException executionError(String message, Throwable cause) {
    String safe = message == null || message.isBlank() ? "SQL 执行失败" : message;
    return cause == null
        ? new DataSourcePluginException(Operation.EXECUTION, safe)
        : new DataSourcePluginException(Operation.EXECUTION, safe, cause);
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL 执行失败" : throwable.getClass().getSimpleName();
    }
    String sanitized =
        message.replaceAll("(?i)(password|pwd)=([^;&\\s]+)", "$1=******");
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }
}
