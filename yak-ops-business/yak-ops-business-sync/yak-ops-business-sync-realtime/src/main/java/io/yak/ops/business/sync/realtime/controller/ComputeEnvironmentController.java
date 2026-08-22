package io.yak.ops.business.sync.realtime.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.service.ComputeEnvironmentService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "计算引擎运行环境")
@RestController
@RequestMapping("/api/v1/compute-environments")
@RequiresPermission(RealtimePermissionCode.READ)
public class ComputeEnvironmentController {

  private final ComputeEnvironmentService service;

  public ComputeEnvironmentController(ComputeEnvironmentService service) {
    this.service = service;
  }

  public record SaveRequest(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {}

  public record EnabledRequest(boolean enabled) {}

  @Operation(summary = "查询运行环境列表")
  @GetMapping
  public Result<List<ComputeEnvironment>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "查询运行环境详情")
  @GetMapping("/{id}")
  public Result<ComputeEnvironment> detail(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "新建运行环境")
  @PostMapping
  @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> create(@RequestBody SaveRequest request) {
    return Result.success(
        service.create(
            request.name(),
            request.submitterType(),
            request.config(),
            request.enabled(),
            request.makeDefault()));
  }

  @Operation(summary = "更新运行环境")
  @PutMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> update(@PathVariable long id, @RequestBody SaveRequest request) {
    service.update(
        id,
        request.name(),
        request.submitterType(),
        request.config(),
        request.enabled(),
        request.makeDefault());
    return Result.success(true);
  }

  @Operation(summary = "启用或停用运行环境")
  @PutMapping("/{id}/enabled")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> setEnabled(
      @PathVariable long id, @RequestBody EnabledRequest request) {
    service.setEnabled(id, request.enabled());
    return Result.success(true);
  }

  @Operation(summary = "设置默认运行环境")
  @PostMapping("/{id}/default")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> setDefault(@PathVariable long id) {
    service.setDefault(id);
    return Result.success(true);
  }

  @Operation(summary = "删除运行环境")
  @DeleteMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.DELETE)
  public Result<Boolean> delete(@PathVariable long id) {
    service.delete(id);
    return Result.success(true);
  }
}
