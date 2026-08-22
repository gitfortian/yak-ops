package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeDefinitionValidatorTest {

  private Validator beanValidator;
  private CdcPipelineSpecValidator specValidator;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeDataSourceResolver dataSourceResolver;
  private RealtimeConnectorCapabilityResolver capabilityResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeDefinitionValidator validator;
  private ComputeEnvironmentSnapshot environment;
  private ResolvedCdcPipeline resolved;

  @BeforeEach
  void setUp() {
    beanValidator = mock(Validator.class);
    specValidator = mock(CdcPipelineSpecValidator.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    capabilityResolver = mock(RealtimeConnectorCapabilityResolver.class);
    compiler = mock(PipelineYamlCompiler.class);
    gateway = mock(RealtimeEngineGateway.class);
    environment = mock(ComputeEnvironmentSnapshot.class);
    resolved = mock(ResolvedCdcPipeline.class);

    when(beanValidator.validate(any(CdcPipelineSpec.class))).thenReturn(Set.of());
    when(runtimeResolver.environment(3L, true)).thenReturn(environment);
    when(dataSourceResolver.resolve(any(CdcPipelineSpec.class))).thenReturn(resolved);
    ObjectNode manifest = new ObjectMapper().createObjectNode();
    manifest.put("deliverySemantics", "at-least-once");
    manifest.putObject("connectors")
        .putArray("sources")
        .add("mysql");
    manifest.withObject("connectors").putArray("sinks").add("yak-jdbc:mysql");
    manifest.withObject("connectors").putArray("schemaEvolution").add("evolve");
    when(gateway.capabilities(environment)).thenReturn(manifest);

    validator =
        new RealtimeDefinitionValidator(
            beanValidator,
            specValidator,
            runtimeResolver,
            dataSourceResolver,
            capabilityResolver,
            compiler,
            gateway);
  }

  @Test
  void validatesDefinitionWithoutCallingRuntimeHealth() {
    CdcPipelineSpec spec = spec();

    var result = validator.validate(spec, 3L);

    assertThat(result.valid()).isTrue();
    assertThat(result.deliverySemantics()).isEqualTo("at-least-once");
    verify(specValidator).validate(spec);
    verify(runtimeResolver).environment(3L, true);
    verify(dataSourceResolver).resolve(spec);
    verify(capabilityResolver).requireSupported(any(), eq(resolved), eq(spec));
    verify(compiler).compile("definition-preflight", spec, resolved);
    verify(gateway, never()).validate(any(), any());
  }

  @Test
  void rejectsDatasourceResolutionFailureBeforePersistence() {
    CdcPipelineSpec spec = spec();
    when(dataSourceResolver.resolve(spec))
        .thenThrow(new IllegalArgumentException("Source 数据源不存在：1"));

    assertThatThrownBy(() -> validator.validate(spec, 3L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Source 数据源不存在");

    verify(compiler, never()).compile(any(), any(), any());
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
}
