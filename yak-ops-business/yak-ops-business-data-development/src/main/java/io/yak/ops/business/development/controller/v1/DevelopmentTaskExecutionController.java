package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.domain.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.domain.DevelopmentTaskExecutionPage;
import io.yak.ops.business.development.service.DevelopmentTaskExecutionService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据开发运行记录接口")
@RestController
@RequestMapping("/api/v1/data-development/executions")
public class DevelopmentTaskExecutionController {

  private final DevelopmentTaskExecutionService service;

  public DevelopmentTaskExecutionController(DevelopmentTaskExecutionService service) {
    this.service = service;
  }

  @Operation(summary = "分页查询数据开发运行记录")
  @GetMapping
  public Result<DevelopmentTaskExecutionPage> page(
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String taskType,
      @RequestParam(required = false) String triggerType,
      @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
      @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
    return Result.success(
        service.page(pageNo, pageSize, keyword, status, taskType, triggerType, startTime, endTime));
  }

  @Operation(summary = "查询数据开发运行记录详情")
  @GetMapping("/{id}")
  public Result<DevelopmentTaskExecutionDetail> get(@PathVariable("id") Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("运行记录 ID 非法");
    return Result.success(service.get(id));
  }
}
