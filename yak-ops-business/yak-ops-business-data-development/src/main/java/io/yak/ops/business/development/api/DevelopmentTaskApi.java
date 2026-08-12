package io.yak.ops.business.development.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** HTTP request contracts for data-development task authoring. */
public final class DevelopmentTaskApi {

  private DevelopmentTaskApi() {
  }

  public record SaveDraftRequest(
      @NotBlank String taskType,
      @Min(1) int schemaVersion,
      String content,
      String configJson,
      @PositiveOrZero Long baseRevision) {
  }

  public record PublishRequest(
      @NotNull @Positive Long draftRevision) {
  }
}
