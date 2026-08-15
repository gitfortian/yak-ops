package io.yak.ops.business.dashboard;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 仪表盘接口")
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @Operation(summary = "查询 Dashboard 列表")
  @GetMapping
  public Result<List<DashboardAsset>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "查询 Dashboard 当前草稿和历史版本")
  @GetMapping("/{dashboardId}")
  public Result<DashboardDetail> get(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.get(dashboardId));
  }

  @Operation(summary = "查询 Dashboard 版本历史")
  @GetMapping("/{dashboardId}/versions")
  public Result<List<DashboardVersion>> versions(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.versions(dashboardId));
  }

  @Operation(summary = "查看指定 DashboardVersion 快照")
  @GetMapping("/{dashboardId}/versions/{versionNo}")
  public Result<DashboardVersionDetail> version(
      @PathVariable("dashboardId") long dashboardId,
      @PathVariable("versionNo") int versionNo) {
    return Result.success(service.version(dashboardId, versionNo));
  }

  @Operation(summary = "查询 Dashboard 当前已发布快照")
  @GetMapping("/{dashboardId}/published")
  public Result<DashboardVersionDetail> published(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.published(dashboardId));
  }

  @Operation(summary = "创建 Dashboard，并保存草稿 V1")
  @PostMapping
  public Result<DashboardDetail> create(@Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(service.create(toCommand(request)));
  }

  @Operation(summary = "保存 Dashboard 新草稿版本")
  @PostMapping("/{dashboardId}/versions")
  public Result<DashboardDetail> saveVersion(
      @PathVariable("dashboardId") long dashboardId,
      @Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(service.saveVersion(dashboardId, toCommand(request)));
  }

  @Operation(summary = "发布当前 Dashboard 草稿")
  @PostMapping("/{dashboardId}/publish")
  public Result<DashboardDetail> publish(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.publish(dashboardId));
  }

  @Operation(summary = "将历史 DashboardVersion 恢复为新的草稿版本")
  @PostMapping("/{dashboardId}/restore/{versionNo}")
  public Result<DashboardDetail> restoreVersion(
      @PathVariable("dashboardId") long dashboardId,
      @PathVariable("versionNo") int versionNo) {
    return Result.success(service.restoreVersion(dashboardId, versionNo));
  }

  @Deprecated
  @Operation(summary = "兼容旧版激活接口：恢复历史版本为新草稿")
  @PostMapping("/{dashboardId}/activate/{versionNo}")
  public Result<DashboardDetail> activateVersion(
      @PathVariable("dashboardId") long dashboardId,
      @PathVariable("versionNo") int versionNo) {
    return Result.success(service.activateVersion(dashboardId, versionNo));
  }

  @Operation(summary = "删除 Dashboard 及其历史版本")
  @DeleteMapping("/{dashboardId}")
  public Result<Boolean> delete(@PathVariable("dashboardId") long dashboardId) {
    service.delete(dashboardId);
    return Result.success(Boolean.TRUE);
  }

  private DashboardService.SaveCommand toCommand(SaveDashboardRequest request) {
    List<DashboardService.WidgetSpec> widgets = request.widgets() == null ? List.of() : request.widgets().stream()
        .map(widget -> new DashboardService.WidgetSpec(
            widget.widgetKey(), widget.analysisId(), widget.title(), widget.inlineAnalysis(),
            widget.x(), widget.y(), widget.w(), widget.h(), widget.minW(), widget.minH()))
        .toList();
    List<DashboardService.GlobalFilterSpec> filters = request.globalFilters() == null ? List.of()
        : request.globalFilters().stream()
            .map(filter -> new DashboardService.GlobalFilterSpec(
                filter.filterKey(), filter.name(), filter.operator(), filter.defaultValue(),
                filter.bindings() == null ? List.of() : filter.bindings().stream()
                    .map(binding -> new DashboardService.FilterBindingSpec(binding.widgetKey(), binding.fieldId()))
                    .toList()))
            .toList();
    List<DashboardService.InteractionSpec> interactions = request.interactions() == null ? List.of()
        : request.interactions().stream()
            .map(interaction -> new DashboardService.InteractionSpec(
                interaction.interactionKey(), interaction.event(), interaction.sourceWidgetKey(),
                interaction.sourceFieldId(), interaction.targetFilterKey()))
            .toList();
    return new DashboardService.SaveCommand(
        request.name(), request.description(), request.activeDatasetId(), widgets, filters, interactions);
  }

  public record SaveDashboardRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @Min(1) Long activeDatasetId,
      @Size(max = 200) List<@Valid WidgetRequest> widgets,
      @Size(max = 20) List<@Valid GlobalFilterRequest> globalFilters,
      @Size(max = 100) List<@Valid InteractionRequest> interactions) {
  }

  public record WidgetRequest(
      @NotBlank @Size(max = 64) String widgetKey,
      @Min(1) Long analysisId,
      @Size(max = 200) String title,
      Object inlineAnalysis,
      @Min(0) @Max(23) int x,
      @Min(0) int y,
      @Min(1) @Max(24) int w,
      @Min(1) @Max(60) int h,
      @Min(1) @Max(24) Integer minW,
      @Min(1) @Max(60) Integer minH) {
  }

  public record GlobalFilterRequest(
      @NotBlank @Size(max = 64) String filterKey,
      @NotBlank @Size(max = 200) String name,
      @NotNull DashboardGlobalFilterOperator operator,
      Object defaultValue,
      @Size(max = 200) List<@Valid FilterBindingRequest> bindings) {
  }

  public record FilterBindingRequest(
      @NotBlank @Size(max = 64) String widgetKey,
      @NotBlank @Size(max = 64) String fieldId) {
  }

  public record InteractionRequest(
      @NotBlank @Size(max = 64) String interactionKey,
      @NotNull DashboardInteractionEvent event,
      @NotBlank @Size(max = 64) String sourceWidgetKey,
      @NotBlank @Size(max = 64) String sourceFieldId,
      @NotBlank @Size(max = 64) String targetFilterKey) {
  }
}
