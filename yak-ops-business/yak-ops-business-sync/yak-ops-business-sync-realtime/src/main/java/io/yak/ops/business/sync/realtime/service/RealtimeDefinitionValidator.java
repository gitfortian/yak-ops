package io.yak.ops.business.sync.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Unified definition-level validation boundary shared by draft preflight and save flows.
 *
 * <p>This deliberately does not call Flink REST health checks. A draft must remain saveable when the
 * remote cluster is temporarily offline; publish/start still perform runtime validation through the
 * existing job lifecycle path.
 */
@Service
public class RealtimeDefinitionValidator {

  private final Validator beanValidator;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;

  public RealtimeDefinitionValidator(
      Validator beanValidator,
      CdcPipelineSpecValidator specValidator,
      RealtimeRuntimeResolver runtimeResolver,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway) {
    this.beanValidator = beanValidator;
    this.specValidator = specValidator;
    this.runtimeResolver = runtimeResolver;
    this.dataSourceResolver = dataSourceResolver;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
  }

  public RealtimeValidationResult validate(CdcPipelineSpec spec, long runtimeEnvironmentId) {
    validateSpec(spec);
    ComputeEnvironmentSnapshot environment = runtimeResolver.environment(runtimeEnvironmentId, true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities(environment);
    capabilityResolver.requireSupported(manifest, resolved, spec);

    // Compile the logical definition as part of preflight so route escaping and connector YAML shape
    // fail before persistence. No secrets are resolved and no Flink REST call is made here.
    compiler.compile("definition-preflight", spec, resolved);

    return new RealtimeValidationResult(
        true, manifest.path("deliverySemantics").asText("at-least-once"));
  }

  public void validateSpec(CdcPipelineSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("实时同步 Spec 不能为空");
    }
    List<String> violations =
        beanValidator.validate(spec).stream()
            .sorted(Comparator.comparing(value -> value.getPropertyPath().toString()))
            .map(this::violationMessage)
            .toList();
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException("实时同步 Spec 配置无效：" + String.join("；", violations));
    }
    specValidator.validate(spec);
  }

  private String violationMessage(ConstraintViolation<CdcPipelineSpec> violation) {
    String path = violation.getPropertyPath().toString();
    return (path.isBlank() ? "配置" : path) + " " + violation.getMessage();
  }
}
