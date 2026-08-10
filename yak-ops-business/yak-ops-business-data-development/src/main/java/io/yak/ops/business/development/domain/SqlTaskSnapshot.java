package io.yak.ops.business.development.domain;

import java.util.List;

/** JSON payloads stored inside the generic {@code TaskVersionSnapshot}. */
public final class SqlTaskSnapshot {

  private SqlTaskSnapshot() {}

  public record Definition(String sql, List<SqlParameterDefinition> parameters) {
    public Definition {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
  }

  public record ExecutionConfig(Long dataSourceId, Long taskVersionId) {}
}
