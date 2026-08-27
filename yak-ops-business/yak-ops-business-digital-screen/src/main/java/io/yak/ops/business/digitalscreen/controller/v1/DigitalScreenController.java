package io.yak.ops.business.digitalscreen.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.application.DigitalScreenApplicationService;
import io.yak.ops.business.digitalscreen.controller.v1.converter.DigitalScreenRequestConverter;
import io.yak.ops.business.digitalscreen.controller.v1.converter.DigitalScreenViewConverter;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.CreateDigitalScreenRequest;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.UpdateDigitalScreenRequest;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Digital Screen HTTP adapter. Runtime Dataset queries remain in the Dataset module. */
@Tag(name = "数字化大屏接口")
@RestController
@Validated
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/digital-screens")
public class DigitalScreenController {

  private final DigitalScreenApplicationService service;
  private final DigitalScreenRequestConverter requestConverter;
  private final DigitalScreenViewConverter viewConverter;

  @Operation(summary = "查询数字化大屏列表")
  @GetMapping
  public Result<List<DigitalScreenVO>> list() {
    return Result.success(service.list().stream().map(viewConverter::screen).toList());
  }

  @Operation(summary = "查询数字化大屏详情")
  @GetMapping("/{screenId}")
  public Result<DigitalScreenVO> get(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.get(screenId)));
  }

  @Operation(summary = "创建数字化大屏")
  @PostMapping
  public Result<DigitalScreenVO> create(
      @Valid @RequestBody CreateDigitalScreenRequest request) {
    return Result.success(viewConverter.screen(service.create(requestConverter.create(request))));
  }

  @Operation(summary = "更新数字化大屏定义")
  @PutMapping("/{screenId}")
  public Result<DigitalScreenVO> update(
      @PathVariable("screenId") long screenId,
      @Valid @RequestBody UpdateDigitalScreenRequest request) {
    return Result.success(viewConverter.screen(service.update(screenId, requestConverter.update(request))));
  }

  @Operation(summary = "发布数字化大屏")
  @PostMapping("/{screenId}/publish")
  public Result<DigitalScreenVO> publish(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.publish(screenId)));
  }

  @Operation(summary = "取消发布数字化大屏")
  @PostMapping("/{screenId}/offline")
  public Result<DigitalScreenVO> offline(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.offline(screenId)));
  }

  @Operation(summary = "复制数字化大屏")
  @PostMapping("/{screenId}/duplicate")
  public Result<DigitalScreenVO> duplicate(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.duplicate(screenId)));
  }

  @Operation(summary = "删除数字化大屏")
  @DeleteMapping("/{screenId}")
  public Result<Boolean> delete(@PathVariable("screenId") long screenId) {
    service.delete(screenId);
    return Result.success(Boolean.TRUE);
  }
}
