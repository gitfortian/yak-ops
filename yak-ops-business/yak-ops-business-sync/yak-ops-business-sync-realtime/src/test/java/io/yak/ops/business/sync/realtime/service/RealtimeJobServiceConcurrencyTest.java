package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.MatchMode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.RestartPolicy;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.SchemaEvolution;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.SinkTuning;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec.TableRoute;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBinding;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.DeployResult;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.ValidationResult;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeJobServiceConcurrencyTest {

  private static final long JOB_ID = 7L;
  private static final long DEPLOYMENT_ID = 19L;

  private RealtimeJobStore store;
  private CdcPipelineSpecValidator specValidator;
  private RealtimeDataSourceResolver dataSourceResolver;
  private RealtimeConnectorCapabilityResolver capabilityResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobService service;
  private CdcPipelineSpec spec;
  private DefinitionRow stopped;
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
    when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());

    ObjectMapper json = new ObjectMapper();
    ObjectNode capabilities = json.createObjectNode().put("runtimeVersion", "flink-cdc-cli-3.6.0");
    environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "test-env",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://127.0.0.1:8081", "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
            2);
    when(runtimeResolver.definition(any(), eq(true))).thenReturn(environment);
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);
    when(runtimeResolver.environment(anyLong(), eq(true))).thenReturn(environment);
    when(gateway.capabilities(environment)).thenReturn(capabilities);
    when(gateway.validate(eq(environment), any())).thenReturn(new ValidationResult(true, "exactly-once"));
    when(compiler.compile(any(), any(), any())).thenReturn(new CompiledPipeline("pipeline: test", "test"));

    spec =
        new CdcPipelineSpec(
            1L,
            2L,
            List.of(new TableRoute("source_table", "sink_table", MatchMode.EXACT, List.of("id"))),
            "initial",
            SchemaEvolution.EVOLVE,
            1,
            10_000,
            new RestartPolicy("none", 0, 0),
            new SinkTuning(0, 100, 1000, 1024, 16, true));
    stopped = definition("STOPPED", "STOPPED");
    when(store.spec(any())).thenReturn(spec);
    when(store.runtimeEnvironmentId(JOB_ID)).thenReturn(environment.id());

    service =
        new RealtimeJobService(
            store,
            json,
            specValidator,
            new RealtimeStateMachine(),
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway,
            runtimeResolver,
            transactionManager);
  }

  @Test
  void rejectsDifferentKeyWhenAnotherInstanceAlreadyClaimedStarting() {
    when(store.deploymentByIdempotencyKey("start-2")).thenReturn(Optional.empty());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(stopped));
    when(store.lockDefinition(JOB_ID)).thenReturn(definition("RUNNING", "STARTING"));

    assertThatThrownBy(() -> service.start(JOB_ID, "start-2"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("请勿重复启动");

    verify(store, never()).insertDeployment(any(), any(), any(), any(), any(), any());
    verify(gateway, never()).deploy(any(), any());
  }

  @Test
  void cancelsSubmittedJobWhenStopWinsDuringCliSubmission() {
    DefinitionRow stopping = definition("STOPPED", "STOPPING");
    DeploymentRow deployment = deployment("job-123", "STOPPING");

    when(store.deploymentByIdempotencyKey("restart-safe")).thenReturn(Optional.empty());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(stopped));
    when(store.lockDefinition(JOB_ID)).thenReturn(stopped, stopping, stopping);
    when(store.insertDeployment(any(), any(), any(), any(), eq(environment), eq("restart-safe")))
        .thenReturn(DEPLOYMENT_ID);
    when(dataSourceResolver.resolveCredentials(spec))
        .thenReturn(
            new CredentialBinding[] {
              new CredentialBinding("source", "secret"),
              new CredentialBinding("sink", "secret")
            });
    when(gateway.deploy(eq(environment), any()))
        .thenReturn(new DeployResult("job-123", "exactly-once"));
    when(gateway.status(environment, "job-123"))
        .thenReturn(
            new RuntimeStatus("job-123", RuntimeStatus.State.RUNNING),
            new RuntimeStatus("job-123", RuntimeStatus.State.TERMINATED));
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment));

    assertThatCode(() -> service.start(JOB_ID, "restart-safe")).doesNotThrowAnyException();

    verify(store, never()).markDeploymentRunning(anyLong(), anyLong(), any(), any());
    verify(store)
        .bindDeploymentForStop(
            DEPLOYMENT_ID, "job-123", "flink-cdc-cli-3.6.0@env-3-v2");
    verify(gateway).stop(environment, "job-123");
    verify(store).reconcile(JOB_ID, DEPLOYMENT_ID, "STOPPED", "STOPPED", "job-123", null);
  }

  private DefinitionRow definition(String desired, String observed) {
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        "{}",
        "PUBLISHED",
        desired,
        observed,
        1,
        1,
        "digest",
        null,
        null,
        null);
  }

  private DeploymentRow deployment(String engineJobId, String status) {
    return new DeploymentRow(
        DEPLOYMENT_ID,
        JOB_ID,
        1,
        "{}",
        "test",
        "digest",
        "restart-safe",
        engineJobId,
        "flink-cdc-cli-3.6.0@env-3-v2",
        status,
        false,
        null,
        null,
        null);
  }
}
