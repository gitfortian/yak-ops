package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBinding;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.DeployResult;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.ValidationResult;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeExecutionPathTest {

  private static final long JOB_ID = 7L;
  private static final long DEPLOYMENT_ID = 19L;
  private static final String FLINK_JOB_ID = "0123456789abcdef0123456789abcdef";
  private static final String CANONICAL_YAML =
      "source:\n  type: mysql\nsink:\n  type: yak-jdbc\npipeline:\n  name: test-job\n";

  @Test
  void wizardAndYamlSpecsCompileToTheSameRuntimePipeline() {
    CdcPipelineSpec wizardSpec = spec();
    RealtimeDefinitionValidator specValidator =
        new RealtimeDefinitionValidator(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new CdcPipelineSpecValidator(),
            null,
            null,
            null,
            null,
            null,
            null);
    RealtimeYamlCodec yamlCodec = new RealtimeYamlCodec(specValidator);

    String logicalYaml = yamlCodec.render(wizardSpec);
    CdcPipelineSpec yamlSpec = yamlCodec.parse(logicalYaml);

    ResolvedCdcPipeline resolved =
        new ResolvedCdcPipeline(
            new ResolvedCdcPipeline.Endpoint(
                1L,
                "source",
                DataSourceDbType.MYSQL,
                "mysql-source",
                3306,
                "jdbc:mysql://mysql-source:3306/shop",
                "com.mysql.cj.jdbc.Driver",
                "reader",
                "shop"),
            new ResolvedCdcPipeline.Endpoint(
                2L,
                "sink",
                DataSourceDbType.MYSQL,
                "mysql-sink",
                3306,
                "jdbc:mysql://mysql-sink:3306/dw",
                "com.mysql.cj.jdbc.Driver",
                "writer",
                "dw"));
    PipelineYamlCompiler compiler = new PipelineYamlCompiler();

    String wizardPipeline = compiler.compile("test-job", wizardSpec, resolved).yaml();
    String yamlPipeline = compiler.compile("test-job", yamlSpec, resolved).yaml();

    assertThat(yamlSpec).isEqualTo(wizardSpec);
    assertThat(yamlPipeline).isEqualTo(wizardPipeline);
  }

  @Test
  void publishAndStartUseTheSameCompiledPipelineYaml() {
    RealtimeJobStore store = mock(RealtimeJobStore.class);
    CdcPipelineSpecValidator specValidator = mock(CdcPipelineSpecValidator.class);
    RealtimeDataSourceResolver dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    RealtimeConnectorCapabilityResolver capabilityResolver =
        mock(RealtimeConnectorCapabilityResolver.class);
    PipelineYamlCompiler compiler = mock(PipelineYamlCompiler.class);
    RealtimeEngineGateway gateway = mock(RealtimeEngineGateway.class);
    RealtimeRuntimeResolver runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    ObjectMapper json = new ObjectMapper();
    CdcPipelineSpec spec = spec();
    ComputeEnvironmentSnapshot environment = environment();
    ResolvedCdcPipeline resolved = mock(ResolvedCdcPipeline.class);
    DefinitionRow publishedStopped = definition(spec, environment, "STOPPED", "STOPPED");
    DefinitionRow starting = definition(spec, environment, "RUNNING", "STARTING");
    DeploymentRow deployment = deployment(spec, environment);

    ObjectNode capabilities = json.createObjectNode();
    capabilities.put("runtimeVersion", "flink-cdc-cli-3.6.0");
    capabilities.put("deliverySemantics", "at-least-once");

    when(store.definition(JOB_ID)).thenReturn(Optional.of(publishedStopped));
    when(store.spec(any())).thenReturn(spec);
    when(store.runtimeEnvironmentId(JOB_ID)).thenReturn(environment.id());
    when(store.lockDefinition(JOB_ID)).thenReturn(publishedStopped, publishedStopped, starting);
    when(store.deploymentByIdempotencyKey("exec-1")).thenReturn(Optional.empty());
    when(store.insertDeployment(any(), eq(spec), any(), any(), eq(environment), eq("exec-1")))
        .thenReturn(DEPLOYMENT_ID);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment));
    when(runtimeResolver.definition(any(), eq(true))).thenReturn(environment);
    when(dataSourceResolver.resolve(spec)).thenReturn(resolved);
    when(dataSourceResolver.resolveCredentials(spec))
        .thenReturn(
            new CredentialBinding[] {
              new CredentialBinding("source", "source-secret"),
              new CredentialBinding("sink", "sink-secret")
            });
    when(gateway.capabilities(environment)).thenReturn(capabilities);
    when(compiler.compile("test-job", spec, resolved))
        .thenReturn(new CompiledPipeline(CANONICAL_YAML, "mysql#1 -> mysql#2, tables=1"));
    when(gateway.validate(environment, CANONICAL_YAML))
        .thenReturn(new ValidationResult(true, "at-least-once"));
    when(gateway.deploy(eq(environment), any()))
        .thenReturn(new DeployResult(FLINK_JOB_ID, "at-least-once"));

    RealtimeJobService service =
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

    service.publish(JOB_ID);
    service.start(JOB_ID, "exec-1");

    verify(compiler, times(2)).compile("test-job", spec, resolved);
    verify(gateway, times(2)).validate(environment, CANONICAL_YAML);

    ArgumentCaptor<RealtimeDeployRequest> requestCaptor =
        ArgumentCaptor.forClass(RealtimeDeployRequest.class);
    verify(gateway).deploy(eq(environment), requestCaptor.capture());
    assertThat(requestCaptor.getValue().pipelineYaml()).isEqualTo(CANONICAL_YAML);
    assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo("exec-1");

    verify(store)
        .insertDeployment(any(), eq(spec), any(), any(), eq(environment), eq("exec-1"));
    verify(store).markDeploymentRunning(JOB_ID, DEPLOYMENT_ID, FLINK_JOB_ID, environment.runtimeRevision());
  }

  private CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));
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

  private DefinitionRow definition(
      CdcPipelineSpec spec,
      ComputeEnvironmentSnapshot environment,
      String desiredState,
      String observedState) {
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        spec,
        environment.id(),
        "PUBLISHED",
        desiredState,
        observedState,
        1,
        1,
        "definition-digest",
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private DeploymentRow deployment(
      CdcPipelineSpec spec, ComputeEnvironmentSnapshot environment) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        DEPLOYMENT_ID,
        JOB_ID,
        1,
        spec,
        "mysql#1 -> mysql#2, tables=1",
        "pipeline-digest",
        "exec-1",
        FLINK_JOB_ID,
        environment.runtimeRevision(),
        environment,
        "RUNNING",
        false,
        null,
        now,
        now);
  }
}
