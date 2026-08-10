package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.api.DevelopmentDirectoryApi.CreateRequest;
import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.service.DevelopmentDirectoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project-scoped directory management for data development. */
@Tag(name = "数据开发目录接口")
@RestController
@RequestMapping("/api/v1/data-development/directories")
public class DevelopmentDirectoryController {

  private final DevelopmentDirectoryService service;

  public DevelopmentDirectoryController(DevelopmentDirectoryService service) {
    this.service = service;
  }

  @Operation(summary = "查询项目数据开发目录")
  @GetMapping
  public Result<List<DevelopmentDirectory>> list(
      @RequestParam(name = "projectId") Long projectId) {
    return Result.success(service.list(projectId));
  }

  @Operation(summary = "新建数据开发目录")
  @PostMapping
  public Result<DevelopmentDirectory> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(
        request.projectId(),
        request.parentId(),
        request.name()));
  }
}
