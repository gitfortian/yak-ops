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
import io.yak.ops.business.datasource.service.DataSourceCatalogService;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeDefinitionValidatorTest {

  private CdcPipelineSpecValidator specValidator;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeDataSourceResolver dataSourceResolver;
  private DataSourceCatalogService catalogService;
  private RealtimeConnectorCapabilityResolver capabilityResolver;
  private PipelineYamlCompiler compiler;
  private RealtimeEngineGateway gateway;
  private RealtimeDefinitionValidator validator;
  private ComputeEnvironmentSnapshot environment;
  private ResolvedCdcPipeline resolved;

  @BeforeEach
  void setUp() {
    specValidator = mock(CdcPipelineSpecValidator.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    dataSourceResolver = mock(RealtimeDataSourceResolver.class);
    catalogService = mock(DataSourceCatalogService.class);
    capabilityResolver = mock(RealtimeConnectorCapabilityResolver.class);
    compiler = mock(PipelineYamlCompiler.class);
    gateway = mock(RealtimeEngineGateway.class);
    environment = mock(ComputeEnvironmentSnapshot.class);
    resolved = mock(ResolvedCdcPipeline.class);

    when(runtimeResolver.environment(3L, true)).thenReturn(environment);
    when(dataSourceResolver.resolve(any(CdcPipelineSpec.class))).thenReturn(resolved);
    when(catalogService.listTables(1L, null, null, null))
        .thenReturn(List.of(new DataSourceCatalogTableVO("shop", null, "orders", "TABLE", null)));
    when(catalogService.listColumns(1L, "shop", null, "orders"))
        .thenReturn(
            List.of(
                new DataSourceCatalogColumnVO(
                    "id", "BIGINT", null, null, null, false, 1, true, null)));

    ObjectNode manifest = new ObjectMapper().createObjectNode();
    manifest.put("deliverySemantics", "at-least-once");
    ObjectNode connectors = manifest.putObject("connectors");
    connectors.putArray("sources").add("mysql");
    connectors.putArray("sinks").add("yak-jdbc:mysql");
    connectors.putArray("schemaEvolution").add("evolve");
    when(gateway.capabilities(environment)).thenReturn(manifest);

    validator =
        new RealtimeDefinitionValidator(
            Validation.buildDefaultValidatorFactory().getValidator(),
            specValidator,
            runtimeResolver,
            dataSourceResolver,
            catalogService,
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
    verify(catalogService).listTables(1L, null, null, null);
    verify(catalogService).listColumns(1L, "shop", null, "orders");
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

    verify(catalogService, never()).listTables(any(), any(), any(), any());
    verify(compiler, never()).compile(any(), any(), any());
  }

  @Test
  void rejectsPrimaryKeyDriftBeforePersistence() {
    CdcPipelineSpec spec = spec();
    when(catalogService.listColumns(1L, "shop", null, "orders"))
        .thenReturn(
            List.of(
                new DataSourceCatalogColumnVO(
                    "order_id", "BIGINT", null, null, null, false, 1, true, null)));

    assertThatThrownBy(() -> validator.validate(spec, 3L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("主键与任务配置不一致")
        .hasMessageContaining("order_id");

    verify(capabilityResolver, never()).requireSupported(any(), any(), any());
    verify(compiler, never()).compile(any(), any(), any());
  }

  @Test
  void rejectsBeanValidationFailureBeforeDatasourceResolution() {
    CdcPipelineSpec invalid =
        new CdcPipelineSpec(
            1L,
            2L,
            List.of(
                new CdcPipelineSpec.TableRoute(
                    "orders", "orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
            "initial",
            CdcPipelineSpec.SchemaEvolution.EVOLVE,
            0,
            60_000,
            new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
            new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));

    assertThatThrownBy(() -> validator.validate(invalid, 3L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parallelism");

    verify(dataSourceResolver, never()).resolve(any());
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
