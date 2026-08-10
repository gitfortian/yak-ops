package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.api.SqlDevelopmentApi.CreateRequest;
import io.yak.ops.business.development.api.SqlDevelopmentApi.PublishRequest;
import io.yak.ops.business.development.api.SqlDevelopmentApi.RunRequest;
import io.yak.ops.business.development.api.SqlDevelopmentApi.UpdateRequest;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Definition;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Execution;
import io.yak.ops.business.development.domain.SqlDevelopmentModel.Version;
import io.yak.ops.business.development.service.SqlDevelopmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** First-phase SQL data-development backend. */
@Tag(name = "SQL 数据开发接口")
@RestController
@RequestMapping("/api/v1/data-development/sql-tasks")
public class SqlDevelopmentController {

  private final SqlDevelopmentService service;

  public SqlDevelopmentController(SqlDevelopmentService service) {
    this.service = service;
  }

  @Operation(summary = "创建 SQL 开发任务")
  @PostMapping
  public Result<Definition> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(
        request.name(),
        request.description(),
        request.projectId(),
        request.directoryId(),
        request.dataSourceId(),
        request.sql(),
        request.parameters()));
  }

  @Operation(summary = "查询 SQL 开发任务")
  @GetMapping
  public Result<List<Definition>> list(
      @RequestParam(name = "projectId", required = false) Long projectId) {
    return Result.success(service.list(projectId));
  }

  @Operation(summary = "查询 SQL 开发任务详情")
  @GetMapping("/{id}")
  public Result<Definition> detail(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "保存 SQL 草稿")
  @PutMapping("/{id}")
  public Result<Definition> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody UpdateRequest request) {
    return Result.success(service.update(
        id,
        request.baseRevision(),
        request.name(),
        request.description(),
        request.projectId(),
        request.directoryId(),
        request.dataSourceId(),
        request.sql(),
        request.parameters()));
  }

  @Operation(summary = "发布不可变 SQL 版本")
  @PostMapping("/{id}/publish")
  public Result<Version> publish(
      @PathVariable("id") Long id,
      @Valid @RequestBody PublishRequest request) {
    return Result.success(service.publish(id, request.draftRevision()));
  }

  @Operation(summary = "查询 SQL 发布版本")
  @GetMapping("/{id}/versions")
  public Result<List<Version>> versions(@PathVariable("id") Long id) {
    return Result.success(service.versions(id));
  }

  @Operation(summary = "测试运行当前 SQL 草稿")
  @PostMapping("/{id}/run")
  public Result<Execution> run(
      @PathVariable("id") Long id,
      @RequestBody(required = false) RunRequest request) {
    return Result.success(service.runDraft(id, request == null ? null : request.input()));
  }

  @Operation(summary = "查询 SQL 执行状态")
  @GetMapping("/executions/{executionId}")
  public Result<Execution> execution(@PathVariable("executionId") Long executionId) {
    return Result.success(service.execution(executionId));
  }

  @Operation(summary = "取消 SQL 执行")
  @PostMapping("/executions/{executionId}/cancel")
  public Result<Execution> cancel(@PathVariable("executionId") Long executionId) {
    return Result.success(service.cancel(executionId));
  }
}
