package io.yak.ops.core.execution.sql;

/** Shared SQL execution boundary used by product and domain adapters. */
public interface SqlExecutionRuntime {

  SqlExecutionResult execute(SqlExecutionRequest request);
}
