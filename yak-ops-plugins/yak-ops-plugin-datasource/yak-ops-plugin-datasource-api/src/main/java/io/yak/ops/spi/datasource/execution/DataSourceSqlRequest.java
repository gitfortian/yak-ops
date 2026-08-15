package io.yak.ops.spi.datasource.execution;

import java.util.List;

/** Immutable SQL execution request shared by task plugins and datasource plugins. */
public record DataSourceSqlRequest(
    String sql,
    int maxRows,
    int timeoutSeconds,
    List<Object> parameters) {

  /** Backward-compatible constructor for callers that execute SQL without bind parameters. */
  public DataSourceSqlRequest(String sql, int maxRows, int timeoutSeconds) {
    this(sql, maxRows, timeoutSeconds, List.of());
  }

  public DataSourceSqlRequest {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("sql must not be blank");
    }
    sql = sql.trim();
    if (maxRows < 1 || maxRows > 10_000) {
      throw new IllegalArgumentException("maxRows must be between 1 and 10000");
    }
    if (timeoutSeconds < 1 || timeoutSeconds > 3_600) {
      throw new IllegalArgumentException("timeoutSeconds must be between 1 and 3600");
    }
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
  }
}
