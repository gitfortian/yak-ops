package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.service.QualityExecutionWorkspaceService;
import io.yak.ops.common.bean.dto.quality.QualityExecutionWorkspaceDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量执行工作台")
@RestController
@ConditionalOnQualityEnabled
@RequestMapping("/api/v1/data-quality/execution/workspace")
@RequiresPermission(QualityPermissionCode.EXECUTION_READ)
public class QualityExecutionWorkspaceController {
  private final QualityExecutionWorkspaceService service;

  public QualityExecutionWorkspaceController(QualityExecutionWorkspaceService service) {
    this.service = service;
  }

  @Operation(summary = "分页查询监控执行记录")
  @PostMapping("/page")
  public Result<QualityExecutionWorkspaceVO.ExecutionPage> page(
      @Valid @RequestBody(required = false) QualityExecutionWorkspaceDTO.PageRequest request) {
    return Result.success(service.page(request));
  }

  @Operation(summary = "分页查询规则执行记录")
  @PostMapping("/rule/page")
  public Result<QualityExecutionWorkspaceVO.RuleExecutionPage> pageRules(
      @Valid @RequestBody(required = false) QualityExecutionWorkspaceDTO.PageRequest request) {
    return Result.success(service.pageRules(request));
  }

  @Operation(summary = "查询执行工作台详情")
  @GetMapping("/{executionNo}")
  public Result<QualityExecutionWorkspaceVO.ExecutionDetail> detail(@PathVariable String executionNo) {
    return Result.success(service.get(executionNo));
  }

  @Operation(summary = "查询执行结构化日志")
  @GetMapping("/{executionNo}/logs")
  public Result<QualityExecutionWorkspaceVO.LogView> logs(@PathVariable String executionNo) {
    return Result.success(service.logs(executionNo));
  }
}
