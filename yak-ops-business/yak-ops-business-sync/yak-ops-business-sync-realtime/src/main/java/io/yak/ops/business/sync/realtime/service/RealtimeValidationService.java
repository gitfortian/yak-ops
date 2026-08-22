package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import org.springframework.stereotype.Service;

/** HTTP-neutral application boundary for realtime definition and runtime validation. */
@Service
public class RealtimeValidationService {

  private final RealtimeJobService jobs;
  private final RealtimeDefinitionValidator definitions;

  public RealtimeValidationService(
      RealtimeJobService jobs, RealtimeDefinitionValidator definitions) {
    this.jobs = jobs;
    this.definitions = definitions;
  }

  /** Definition-level preflight for unsaved Wizard/YAML/legacy editor payloads. */
  public RealtimeValidationResult validateDefinition(
      CdcPipelineSpec spec, long runtimeEnvironmentId) {
    return definitions.validate(spec, runtimeEnvironmentId);
  }

  /** Runtime validation for the already persisted definition, including Flink REST/CLI readiness. */
  public RealtimeValidationResult validate(long definitionId) {
    var result = jobs.validate(definitionId);
    return new RealtimeValidationResult(result.valid(), result.deliverySemantics());
  }
}
