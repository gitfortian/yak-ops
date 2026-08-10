package io.yak.ops.business.development.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** HTTP request contracts for data-development tree nodes. */
public final class DevelopmentNodeApi {

  private DevelopmentNodeApi() {}

  public record CreateRequest(
      @NotBlank String name,
      @NotBlank String type,
      @Min(0) Long projectId,
      @Min(0) Long directoryId) {
  }
}
