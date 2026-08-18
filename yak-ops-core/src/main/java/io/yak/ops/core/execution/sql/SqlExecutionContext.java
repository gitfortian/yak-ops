package io.yak.ops.core.execution.sql;

import java.util.Objects;

/** Identifies the platform caller without leaking domain-specific models into the SQL runtime. */
public record SqlExecutionContext(SqlExecutionCaller caller, String callerReference) {

  public SqlExecutionContext {
    caller = Objects.requireNonNull(caller, "caller");
    callerReference = normalize(callerReference);
  }

  public static SqlExecutionContext of(SqlExecutionCaller caller, String callerReference) {
    return new SqlExecutionContext(caller, callerReference);
  }

  public static SqlExecutionContext system() {
    return new SqlExecutionContext(SqlExecutionCaller.SYSTEM, null);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
