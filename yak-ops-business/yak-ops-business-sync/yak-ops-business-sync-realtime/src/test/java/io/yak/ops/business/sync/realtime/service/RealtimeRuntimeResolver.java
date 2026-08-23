package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;

/** Test-scope source-compatible alias for the migrated Environment resolver. */
class RealtimeRuntimeResolver
    extends io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver {

  RealtimeRuntimeResolver(ComputeEnvironmentStore environments, RealtimeJobStore jobs) {
    super(environments, jobs);
  }
}
