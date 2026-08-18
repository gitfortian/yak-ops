package io.yak.ops.core.execution.sql;

import java.util.Optional;

/** Platform-level SQL execution boundary shared by product domains. */
public interface SqlExecutionRuntime {

  /**
   * Execute one SQL request synchronously without registering it in the tracked lifecycle registry.
   *
   * <p>This remains the lightweight path for Dataset and Data Service queries.
   */
  SqlExecutionResult execute(SqlExecutionRequest request);

  /** Start one tracked single-statement execution and return its initial snapshot. */
  default SqlExecutionSnapshot start(SqlExecutionRequest request) {
    return start(SqlExecutionPlan.single(request));
  }

  /** Start one tracked execution whose statement boundaries are supplied explicitly by the caller. */
  default SqlExecutionSnapshot start(SqlExecutionPlan plan) {
    throw new UnsupportedOperationException("Tracked SQL execution is not supported");
  }

  /** Find the latest immutable snapshot for a tracked execution. */
  default Optional<SqlExecutionSnapshot> find(String executionId) {
    return Optional.empty();
  }

  /** Wait until a tracked execution reaches a terminal state. */
  default SqlExecutionSnapshot await(String executionId) {
    throw new UnsupportedOperationException("Tracked SQL execution is not supported");
  }

  /** Request cancellation. Returns false when the execution is absent or already terminal. */
  default boolean cancel(String executionId) {
    return false;
  }
}
