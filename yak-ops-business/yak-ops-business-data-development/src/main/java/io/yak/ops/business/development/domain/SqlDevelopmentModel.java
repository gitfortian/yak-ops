package io.yak.ops.business.development.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** SQL development domain models. */
public final class SqlDevelopmentModel {

  private SqlDevelopmentModel() {}

  public record Definition(
      Long id,
      String name,
      String description,
      Long projectId,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters,
      long draftRevision,
      Long publishedVersionId,
      int latestVersionNo,
      Instant createTime,
      Instant updateTime) {

    public Definition {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public String workflowTaskId() {
      return id == null ? null : "SQL:" + id;
    }
  }

  public record Version(
      Long id,
      Long taskId,
      int versionNo,
      Long dataSourceId,
      String sql,
      List<SqlParameterDefinition> parameters,
      String contentDigest,
      Instant publishedAt) {

    public Version {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
  }

  public record Execution(
      Long id,
      Long taskId,
      Long taskVersionId,
      Integer taskVersionNo,
      Long dataSourceId,
      String status,
      long affectedRows,
      Map<String, Object> output,
      String errorMessage,
      Instant createTime,
      Instant startTime,
      Instant finishTime) {

    public Execution {
      output = output == null ? Map.of() : Map.copyOf(output);
    }
  }
}
