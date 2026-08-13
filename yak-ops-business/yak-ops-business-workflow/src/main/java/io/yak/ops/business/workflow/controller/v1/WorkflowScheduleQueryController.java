package io.yak.ops.business.workflow.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.workflow.service.WorkflowScheduleQuery;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工作流调度定义查询接口。 */
@Tag(name = "工作流调度接口")
@RestController
@RequestMapping("/api/v1/workflows/schedules")
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleQueryController {
  private final WorkflowScheduleQuery query;

  public WorkflowScheduleQueryController(WorkflowScheduleQuery query) {
    this.query = query;
  }

  @Operation(summary = "查询工作流调度定义")
  @GetMapping
  public Result<List<WorkflowScheduleVO>> list(
      @RequestParam(value = "workflowId", required = false) String workflowId,
      @RequestParam(value = "status", required = false) String status) {
    return Result.success(query.list(workflowId, status));
  }

  @Operation(summary = "查询工作流调度详情")
  @GetMapping("/{id}")
  public Result<WorkflowScheduleVO> detail(@PathVariable("id") String id) {
    return Result.success(query.get(id));
  }
}
