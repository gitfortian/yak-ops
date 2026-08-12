package io.yak.ops.spi.datasource.execution;

/** Immutable SQL execution request shared by task plugins and datasource plugins. */
public record DataSourceSqlRequest(
    String sql,
    int maxRows,
    int timeoutSeconds) {

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
  }
}
