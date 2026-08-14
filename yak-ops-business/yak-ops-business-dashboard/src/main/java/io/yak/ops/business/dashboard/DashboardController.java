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

  @Operation(summary = "查询 Dashboard 当前版本和历史版本")
  @GetMapping("/{dashboardId}")
  public Result<DashboardDetail> get(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.get(dashboardId));
  }

  @Operation(summary = "查询 Dashboard 版本历史")
  @GetMapping("/{dashboardId}/versions")
  public Result<List<DashboardVersion>> versions(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(service.versions(dashboardId));
  }

  @Operation(summary = "创建 Dashboard，并产生 V1")
  @PostMapping
  public Result<DashboardDetail> create(@Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(service.create(toCommand(request)));
  }

  @Operation(summary = "保存 Dashboard 新版本")
  @PostMapping("/{dashboardId}/versions")
  public Result<DashboardDetail> saveVersion(
      @PathVariable("dashboardId") long dashboardId,
      @Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(service.saveVersion(dashboardId, toCommand(request)));
  }

  @Operation(summary = "激活历史 DashboardVersion")
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
    return new DashboardService.SaveCommand(
        request.name(), request.description(), request.activeDatasetId(),
        request.widgets() == null ? List.of() : request.widgets().stream()
            .map(widget -> new DashboardService.WidgetSpec(
                widget.widgetKey(), widget.analysisId(), widget.title(), widget.inlineAnalysis(),
                widget.x(), widget.y(), widget.w(), widget.h(), widget.minW(), widget.minH()))
            .toList());
  }

  public record SaveDashboardRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @Min(1) Long activeDatasetId,
      @Size(max = 200) List<@Valid WidgetRequest> widgets) {}

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
      @Min(1) @Max(60) Integer minH) {}
}
