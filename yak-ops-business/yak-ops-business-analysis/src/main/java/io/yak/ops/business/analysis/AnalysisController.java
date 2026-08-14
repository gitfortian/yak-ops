package io.yak.ops.business.analysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 图表分析接口")
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

  private final AnalysisService service;

  public AnalysisController(AnalysisService service) {
    this.service = service;
  }

  @Operation(summary = "查询可复用 Analysis 列表")
  @GetMapping
  public Result<List<AnalysisAsset>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "查询 Analysis 详情")
  @GetMapping("/{analysisId}")
  public Result<AnalysisAsset> get(@PathVariable("analysisId") long analysisId) {
    return Result.success(service.get(analysisId));
  }

  @Operation(summary = "创建可复用 Analysis")
  @PostMapping
  public Result<AnalysisAsset> create(@Valid @RequestBody SaveAnalysisRequest request) {
    return Result.success(service.create(toCommand(request)));
  }

  @Operation(summary = "更新 Analysis 定义")
  @PutMapping("/{analysisId}")
  public Result<AnalysisAsset> update(
      @PathVariable("analysisId") long analysisId,
      @Valid @RequestBody SaveAnalysisRequest request) {
    return Result.success(service.update(analysisId, toCommand(request)));
  }

  @Operation(summary = "删除 Analysis")
  @DeleteMapping("/{analysisId}")
  public Result<Boolean> delete(@PathVariable("analysisId") long analysisId) {
    service.delete(analysisId);
    return Result.success(Boolean.TRUE);
  }

  private AnalysisService.SaveCommand toCommand(SaveAnalysisRequest request) {
    AnalysisQuerySpec querySpec = new AnalysisQuerySpec(
        request.querySpec().dimensions() == null ? List.of() : request.querySpec().dimensions(),
        request.querySpec().metrics() == null ? List.of() : request.querySpec().metrics().stream()
            .map(metric -> new AnalysisMetricBinding(metric.fieldId(), metric.aggregation()))
            .toList(),
        request.querySpec().filters() == null ? List.of() : request.querySpec().filters().stream()
            .map(filter -> new AnalysisFilterBinding(filter.fieldId(), filter.operator(), filter.value()))
            .toList(),
        request.querySpec().sorts() == null ? List.of() : request.querySpec().sorts().stream()
            .map(sort -> new AnalysisSortBinding(sort.fieldId(), sort.aggregation(), sort.direction()))
            .toList(),
        request.querySpec().limit() == null ? 0 : request.querySpec().limit(),
        request.querySpec().timeoutSeconds() == null ? 0 : request.querySpec().timeoutSeconds());
    AnalysisVisualConfig visualConfig = request.visualConfig() == null ? null : new AnalysisVisualConfig(
        request.visualConfig().showLegend(),
        request.visualConfig().showDataLabels(),
        request.visualConfig().smooth(),
        request.visualConfig().showGrid());
    return new AnalysisService.SaveCommand(
        request.name(),
        request.description(),
        request.datasetId(),
        request.chartType(),
        querySpec,
        visualConfig);
  }

  public record SaveAnalysisRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @Min(1) long datasetId,
      @NotNull AnalysisChartType chartType,
      @NotNull @Valid QuerySpecRequest querySpec,
      @Valid VisualConfigRequest visualConfig) {
  }

  public record QuerySpecRequest(
      List<@Size(max = 64) String> dimensions,
      List<@Valid MetricRequest> metrics,
      List<@Valid FilterRequest> filters,
      List<@Valid SortRequest> sorts,
      @Min(1) @Max(1000) Integer limit,
      @Min(1) @Max(120) Integer timeoutSeconds) {
  }

  public record MetricRequest(
      @NotBlank @Size(max = 64) String fieldId,
      @NotNull AnalysisAggregation aggregation) {
  }

  public record FilterRequest(
      @NotBlank @Size(max = 64) String fieldId,
      @NotNull AnalysisFilterOperator operator,
      Object value) {
  }

  public record SortRequest(
      @NotBlank @Size(max = 64) String fieldId,
      AnalysisAggregation aggregation,
      AnalysisSortDirection direction) {
  }

  public record VisualConfigRequest(
      boolean showLegend,
      boolean showDataLabels,
      boolean smooth,
      boolean showGrid) {
  }
}
