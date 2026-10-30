package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.governance.service.GovernanceHealthService;
import io.yak.ops.business.governance.service.GovernanceIssueService;
import io.yak.ops.business.governance.service.GovernanceScanService;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueBatchDTO;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueQueryDTO;
import io.yak.ops.common.bean.vo.governance.GovernanceHealthVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueEventVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueVO;
import io.yak.ops.common.bean.vo.governance.GovernanceOverviewVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleVO;
import io.yak.ops.common.bean.vo.governance.GovernanceScanDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceScanVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated HTTP boundary of asset governance.
 *
 * <p>Every route is Workspace-scoped through the existing interceptor; no route accepts a Workspace
 * identifier from the caller.
 */
@Tag(name = "资产治理接口")
@RestController
@Validated
@RequestMapping(CommonConstants.API_PREFIX + "/governance")
public class GovernanceController {

    @Resource
    private GovernanceHealthService healthService;

    @Resource
    private GovernanceIssueService issueService;

    @Resource
    private GovernanceScanService scanService;

    @Resource
    private AuthenticationManager authentication;

    @Operation(summary = "治理总览：最新得分、覆盖度、问题计数与最近一次扫描")
    @GetMapping("/overview")
    public Result<GovernanceOverviewVO> overview() {
        return Result.success(healthService.overview(WorkspaceContext.requireWorkspaceId()));
    }

    @Operation(summary = "分页查询治理问题")
    @GetMapping("/issues")
    public Result<PagingData<GovernanceIssueVO>> issues(@Valid @ModelAttribute GovernanceIssueQueryDTO query) {
        return Result.success(PagingData.from(issueService.page(WorkspaceContext.requireWorkspaceId(), query)));
    }

    @Operation(summary = "治理问题详情，含规则定义与事件流")
    @GetMapping("/issues/{issueId}")
    public Result<GovernanceIssueDetailVO> issue(@PathVariable String issueId) {
        return Result.success(issueService.detail(WorkspaceContext.requireWorkspaceId(), issueId));
    }

    @Operation(summary = "治理问题的事件流，最新在前")
    @GetMapping("/issues/{issueId}/events")
    public Result<PagingData<GovernanceIssueEventVO>> events(
            @PathVariable String issueId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(
                PagingData.from(issueService.events(WorkspaceContext.requireWorkspaceId(), issueId, pageNo, pageSize)));
    }

    @Operation(summary = "批量指派负责人")
    @PostMapping("/issues/assign")
    public Result<Integer> assign(@Valid @RequestBody GovernanceIssueBatchDTO input) {
        return Result.success(issueService.assign(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "批量手动解决")
    @PostMapping("/issues/resolve")
    public Result<Integer> resolve(@Valid @RequestBody GovernanceIssueBatchDTO input) {
        return Result.success(issueService.resolve(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "批量重新打开已解决的问题")
    @PostMapping("/issues/reopen")
    public Result<Integer> reopen(@Valid @RequestBody GovernanceIssueBatchDTO input) {
        return Result.success(issueService.reopen(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "批量忽略到指定时间")
    @PostMapping("/issues/ignore")
    public Result<Integer> ignore(@Valid @RequestBody GovernanceIssueBatchDTO input) {
        return Result.success(issueService.ignore(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "批量取消忽略")
    @PostMapping("/issues/unignore")
    public Result<Integer> unignore(@Valid @RequestBody GovernanceIssueBatchDTO input) {
        return Result.success(issueService.unignore(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "只读规则定义与阈值")
    @GetMapping("/rules")
    public Result<List<GovernanceRuleVO>> rules() {
        return Result.success(healthService.rules());
    }

    @Operation(summary = "按日查询治理得分，最多 90 天")
    @GetMapping("/health")
    public Result<GovernanceHealthVO> health(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(healthService.health(WorkspaceContext.requireWorkspaceId(), from, to));
    }

    @Operation(summary = "分页查询扫描记录，最新在前")
    @GetMapping("/scans")
    public Result<PagingData<GovernanceScanVO>> scans(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(
                PagingData.from(scanService.page(WorkspaceContext.requireWorkspaceId(), pageNo, pageSize)));
    }

    @Operation(summary = "提交一次手动扫描")
    @PostMapping("/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<GovernanceScanVO> submitScan() {
        return Result.success(scanService.submit(WorkspaceContext.requireWorkspaceId(), actor()));
    }

    @Operation(summary = "扫描详情，含每条规则的结果")
    @GetMapping("/scans/{scanId}")
    public Result<GovernanceScanDetailVO> scan(@PathVariable String scanId) {
        return Result.success(scanService.detail(WorkspaceContext.requireWorkspaceId(), scanId));
    }

    private String actor() {
        return authentication.getLoginUserId();
    }
}
