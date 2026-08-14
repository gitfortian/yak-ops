package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.service.WorkflowScheduleCreateCommand;
import io.yak.ops.business.workflow.service.WorkflowScheduleLifecycle;
import io.yak.ops.business.workflow.service.WorkflowScheduleRevision;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作流调度定义变更接口。 */
@Tag(name = "工作流调度接口")
@RestController
@RequestMapping("/api/v1/workflows/schedules")
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleCommandController {
  private final WorkflowScheduleCreateCommand creator;
  private final WorkflowScheduleRevision revision;
  private final WorkflowScheduleLifecycle lifecycle;

  public WorkflowScheduleCommandController(
      WorkflowScheduleCreateCommand creator,
      WorkflowScheduleRevision revision,
      WorkflowScheduleLifecycle lifecycle) {
    this.creator = creator;
    this.revision = revision;
    this.lifecycle = lifecycle;
  }

  @Operation(summary = "创建工作流调度定义")
  @PostMapping
  public Result<WorkflowScheduleVO> create(@Valid @RequestBody WorkflowScheduleCreateDTO request) {
    return Result.success(creator.create(request));
  }

  @Operation(summary = "保存工作流调度配置")
  @PutMapping("/{id}")
  public Result<WorkflowScheduleVO> update(
      @PathVariable("id") String id,
      @Valid @RequestBody WorkflowScheduleUpdateDTO request) {
    return Result.success(revision.save(id, request));
  }

  @Operation(summary = "启用工作流调度定义")
  @PostMapping("/{id}/online")
  public Result<WorkflowScheduleVO> online(@PathVariable("id") String id) {
    return Result.success(lifecycle.online(id));
  }

  @Operation(summary = "停用工作流调度定义")
  @PostMapping("/{id}/offline")
  public Result<WorkflowScheduleVO> offline(@PathVariable("id") String id) {
    return Result.success(lifecycle.offline(id));
  }

  @Operation(summary = "删除工作流调度定义")
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") String id) {
    lifecycle.remove(id);
    return Result.success(Boolean.TRUE);
  }
}
