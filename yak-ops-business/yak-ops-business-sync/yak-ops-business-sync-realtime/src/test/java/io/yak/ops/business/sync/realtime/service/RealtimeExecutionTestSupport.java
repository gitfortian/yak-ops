package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionCoordinator;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionPreparation;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReplacementManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReservationManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStarter;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStateManager;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import org.springframework.transaction.PlatformTransactionManager;

/** Builds the real decomposed Execution Core for behavioral regression tests. */
final class RealtimeExecutionTestSupport {

  private RealtimeExecutionTestSupport() {}

  static RealtimeExecutionCoordinator coordinator(
      RealtimeJobStore store,
      CdcPipelineSpecValidator specValidator,
      SyncExecutionStateMachine stateMachine,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      PlatformTransactionManager transactionManager) {
    RealtimeExecutionPreparation preparation =
        new RealtimeExecutionPreparation(
            store,
            specValidator,
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver);
    RealtimeExecutionReservationManager reservations =
        new RealtimeExecutionReservationManager(
            store, stateMachine, preparation, transactionManager);
    RealtimeExecutionStateManager states =
        new RealtimeExecutionStateManager(
            store, stateMachine, gateway, preparation, transactionManager);
    RealtimeExecutionStarter starter =
        new RealtimeExecutionStarter(preparation, reservations, states);
    RealtimeExecutionReplacementManager replacements =
        new RealtimeExecutionReplacementManager(
            preparation, reservations, states, starter);
    return new RealtimeExecutionCoordinator(starter, states, replacements);
  }
}
