package io.yak.ops.business.taskcatalog.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only discovery API for published task assets. */
@Tag(name = "任务资产目录")
@RestController
@RequestMapping("/api/v1/task-catalog/assets")
@ConditionalOnDataSourceEnabled
public class TaskCatalogController {

  private final TaskCatalogService service;

  public TaskCatalogController(TaskCatalogService service) {
    this.service = service;
  }

  @Operation(summary = "查询已发布任务资产")
  @GetMapping
  public Result<List<TaskAsset>> list(
      @RequestParam(value = "source", required = false) String source,
      @RequestParam(value = "status", required = false, defaultValue = "ONLINE") String status,
      @RequestParam(value = "keyword", required = false) String keyword) {
    return Result.success(service.list(source, status, keyword));
  }
}
