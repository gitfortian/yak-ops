package io.yak.ops.business.sync.realtime.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Stable, password-free control-plane model. Secrets are resolved only by the Runtime environment. */
public record CdcPipelineSpec(
 @NotNull Long sourceDataSourceRef, @NotNull Long sinkDataSourceRef,
 @Valid @NotNull Source source, @Valid @NotNull Sink sink,
 @NotEmpty List<@Valid TableRoute> tables,
 @Pattern(regexp="initial|latest-offset") String startupMode,
 boolean schemaEvolution, @Min(1) @Max(256) int parallelism, @Valid Restart restart) {
 public record Source(@NotBlank String hostname,@Min(1) @Max(65535) int port,@NotBlank String username,@NotBlank String database,@NotBlank String serverId) {}
 public record Sink(@NotBlank String url,@NotBlank String driver,@NotBlank String username,@Pattern(regexp="mysql|postgres") String dialect,@Min(0) int maxRetries,@Min(1) int batchSize,@Min(1) long flushIntervalMs,@Min(1) long maxBatchBytes,@Min(0) int statementCacheSize,boolean replaySafety) {}
 public record TableRoute(@NotBlank String source,@NotBlank String sink) {}
 public record Restart(@Pattern(regexp="fixed-delay|failure-rate|none") String strategy,@Min(0) int attempts,@Min(0) long delayMs) {}
}
