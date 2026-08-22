package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.ValidationResult;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeWave4DefinitionMutationTest {

  private static final long TASK_ID = 7L;
  private static final long RUNNING_VERSION_ID = 31L;
  private static final long NEW_PUBLISHED_VERSION_ID = 32L;

  private RealtimeJobStore store;
  private CdcPipelineSpecValidator specValidator;
  private RealtimeDataSourceResolver dataSourceResolver;
  private RealtimeConnectorCapabilityResolver capabilityResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobService service;
  private CdcPipelineSpec spec;
  private ComputeEnvironmentSnapshot environment;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    specValidator = mock(CdcPipelineSpecValidator.class);
    dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    capabilityResolver = mock(RealtimeConnectorCapabilityResolver.class);
    compiler = mock(PipelineYamlCompiler.class);
    gateway = mock(RealtimeEngineGateway.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    spec = spec();
    environment = environment();
    service =
        new RealtimeJobService(
            store,
            new ObjectMapper(),
            specValidator,
            new SyncExecutionStateMachine(),
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver,
            transactionManager);
  }

  @Test
  void savesNewDraftWhilePreviousExecutionIsRunning() {
    DefinitionRow task = task("PUBLISHED", 3, 3);
    when(runtimeResolver.environment(environment.id(), true)).thenReturn(environment);
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
    when(store.latestExecution(TASK_ID)).thenReturn(Optional.of(runningExecution(RUNNING_VERSION_ID)));

    service.save(TASK_ID, "orders-sync", "changed", spec, environment.id());

    verify(store)
        .updateDefinition(
            TASK_ID, "orders-sync", "changed", spec, any(String.class), environment.id());
    verify(store, never()).markStopping(anyLong(), any());
    verify(store, never()).reconcile(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void publishesNewVersionWhilePreviousExecutionKeepsRunning() {
    DefinitionRow task = task("DRAFT", 4, 3);
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    ObjectNode capabilities = new ObjectMapper().createObjectNode();
    CompiledPipeline compiled = new CompiledPipeline("pipeline: v4", "orders");

    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(store.spec(task)).thenReturn(spec);
    when(store.runtimeEnvironmentId(TASK_ID)).thenReturn(environment.id());
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
    when(store.latestExecution(TASK_ID)).thenReturn(Optional.of(runningExecution(RUNNING_VERSION_ID)));
    when(runtimeResolver.definition(task, true)).thenReturn(environment);
    when(dataSourceResolver.resolve(spec)).thenReturn(resolved);
    when(gateway.capabilities(environment)).thenReturn(capabilities);
    when(compiler.compile("orders-sync", spec, resolved)).thenReturn(compiled);
    when(gateway.validate(environment, compiled.yaml()))
        .thenReturn(new ValidationResult(true, "at-least-once"));

    service.publish(TASK_ID);

    verify(store).publish(TASK_ID, task.definitionVersion(), task.configDigest());
    verify(store, never()).markStopping(anyLong(), any());
    verify(store, never()).markDeploymentRunning(anyLong(), anyLong(), any(), any());
  }

  @Test
  void restartRejectsImplicitUpgradeWhenPublishedRefAdvanced() {
    SyncExecution running = runningExecution(RUNNING_VERSION_ID);
    PublishedDefinitionRow latestPublished =
        new PublishedDefinitionRow(
            NEW_PUBLISHED_VERSION_ID,
            TASK_ID,
            4,
            4,
            spec,
            environment.id(),
            "a".repeat(64),
            "b".repeat(64));

    when(store.deploymentByIdempotencyKey("restart-v3")).thenReturn(Optional.empty());
    when(store.publishedDefinition(TASK_ID)).thenReturn(Optional.of(latestPublished));
    when(store.latestExecution(TASK_ID)).thenReturn(Optional.of(running));

    assertThatThrownBy(() -> service.restart(TASK_ID, "restart-v3"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("拒绝通过重启隐式升级");

    verify(store, never()).markStopping(anyLong(), any());
    verify(gateway, never()).stop(any(), any());
  }

  private DefinitionRow task(String releaseState, int draftRevision, Integer publishedRevision) {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        "test",
        spec,
        environment.id(),
        releaseState,
        "RUNNING",
        "RUNNING",
        draftRevision,
        publishedRevision,
        "c".repeat(64),
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private SyncExecution runningExecution(long definitionVersionId) {
    return new SyncExecution(
        19L,
        TASK_ID,
        definitionVersionId,
        DesiredState.RUNNING,
        ObservedState.RUNNING,
        new EngineExecutionRef("FLINK_CDC", "job-1"),
        false,
        null);
  }

  private ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
        3L,
        "test-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_LOCAL,
        new RuntimeConfig(
            "http://127.0.0.1:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0"),
        2);
  }

  private CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "ods_orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
  }
}
