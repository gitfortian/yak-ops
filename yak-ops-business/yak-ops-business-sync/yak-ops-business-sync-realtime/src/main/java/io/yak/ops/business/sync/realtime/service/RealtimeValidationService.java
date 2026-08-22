package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import org.springframework.stereotype.Service;

/** HTTP-neutral application boundary for validating the current realtime definition. */
@Service
public class RealtimeValidationService {

  private final RealtimeJobService jobs;

  public RealtimeValidationService(RealtimeJobService jobs) {
    this.jobs = jobs;
  }

  public RealtimeValidationResult validate(long definitionId) {
    var result = jobs.validate(definitionId);
    return new RealtimeValidationResult(result.valid(), result.deliverySemantics());
  }
}
