package io.yak.ops.business.development.api;

import io.yak.ops.business.development.domain.SqlParameterDefinition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** HTTP request contracts for first-phase SQL development. */
public final class SqlDevelopmentApi {

  private SqlDevelopmentApi() {}

  public record CreateRequest(
      @NotBlank String name,
      String description,
      @Min(1) Long projectId,
      @Min(0) Long directoryId,
      @NotNull @Min(1) Long dataSourceId,
      @NotBlank String sql,
      List<SqlParameterDefinition> parameters) {
  }

  public record UpdateRequest(
      @Min(1) long baseRevision,
      @NotBlank String name,
      String description,
      @Min(1) Long projectId,
      @Min(0) Long directoryId,
      @NotNull @Min(1) Long dataSourceId,
      @NotBlank String sql,
      List<SqlParameterDefinition> parameters) {
  }

  public record PublishRequest(@Min(1) long draftRevision) {}

  public record RunRequest(Map<String, Object> input) {}
}
