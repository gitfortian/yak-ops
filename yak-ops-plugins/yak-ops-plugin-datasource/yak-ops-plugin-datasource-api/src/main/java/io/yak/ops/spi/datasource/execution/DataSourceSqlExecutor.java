package io.yak.ops.spi.datasource.execution;

/** Fresh datasource SQL executor used by one task execution attempt. */
public interface DataSourceSqlExecutor extends AutoCloseable {

  DataSourceSqlResult execute(DataSourceSqlRequest request);

  /** Best-effort cancellation for a currently running statement. */
  default void cancel() {
    // Optional capability.
  }

  @Override
  default void close() {
    // Optional cleanup capability.
  }
}
