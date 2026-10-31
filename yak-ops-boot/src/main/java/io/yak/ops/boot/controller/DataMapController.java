package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datamap.catalog.DataMapCatalogService;
import io.yak.ops.business.datamap.crawl.DataMapCrawlService;
import io.yak.ops.business.datamap.personal.DataMapPersonalService;
import io.yak.ops.business.datamap.preview.DataMapPreviewService;
import io.yak.ops.business.datamap.task.DataMapCrawlTaskService;
import io.yak.ops.business.datamap.task.DataMapExcludePreviewService;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlExcludePreviewDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSchedulePreviewDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlScopePreviewDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlTaskSaveDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlTaskStatusDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCuratedUpdateDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTableQueryDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlExcludePreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlScopePreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlTaskVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewFacetsVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapPreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableChangeVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datamap.DataMapCrawlStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTriggerType;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP boundary for the Workspace metadata catalog. */
@Tag(name = "数据地图接口")
@RestController
@Validated
@RequestMapping(CommonConstants.API_PREFIX + "/data-map")
public class DataMapController {

    @Resource
    private DataMapCatalogService catalogService;

    @Resource
    private DataMapCrawlService crawlService;

    @Resource
    private DataMapCrawlTaskService taskService;

    @Resource
    private DataMapExcludePreviewService excludePreviewService;

    @Resource
    private DataMapPreviewService previewService;

    @Resource
    private DataMapPersonalService personalService;

    @Resource
    private AuthenticationManager authentication;

    @Operation(summary = "数据地图总览")
    @GetMapping("/overview")
    public Result<DataMapOverviewVO> overview() {
        return Result.success(catalogService.overview(WorkspaceContext.requireWorkspaceId(), actor()));
    }

    @Operation(summary = "数据域与数据源分面计数")
    @GetMapping("/overview/facets")
    public Result<DataMapOverviewFacetsVO> overviewFacets() {
        return Result.success(catalogService.overviewFacets(WorkspaceContext.requireWorkspaceId()));
    }

    @Operation(summary = "搜索与浏览资产")
    @GetMapping("/tables")
    public Result<PagingData<DataMapTableSummaryVO>> tables(@Valid @ModelAttribute DataMapTableQueryDTO query) {
        return Result.success(catalogService.search(WorkspaceContext.requireWorkspaceId(), actor(), query));
    }

    @Operation(summary = "资产详情")
    @GetMapping("/tables/{tableId}")
    public Result<DataMapTableDetailVO> table(@PathVariable String tableId) {
        return Result.success(catalogService.detail(WorkspaceContext.requireWorkspaceId(), actor(), tableId));
    }

    @Operation(summary = "更新资产描述、负责人与数据域")
    @PutMapping("/tables/{tableId}")
    public Result<DataMapTableDetailVO> curate(
            @PathVariable String tableId, @Valid @RequestBody DataMapCuratedUpdateDTO input) {
        return Result.success(catalogService.curate(WorkspaceContext.requireWorkspaceId(), actor(), tableId, input));
    }

    @Operation(summary = "资产变更记录")
    @GetMapping("/tables/{tableId}/changes")
    public Result<List<DataMapTableChangeVO>> changes(@PathVariable String tableId) {
        return Result.success(catalogService.changes(WorkspaceContext.requireWorkspaceId(), actor(), tableId));
    }

    @Operation(summary = "资产数据预览")
    @GetMapping("/tables/{tableId}/preview")
    public Result<DataMapPreviewVO> preview(@PathVariable String tableId) {
        return Result.success(previewService.preview(WorkspaceContext.requireWorkspaceId(), actor(), tableId));
    }

    @Operation(summary = "收藏资产")
    @PostMapping("/tables/{tableId}/favorites")
    public Result<Boolean> addFavorite(@PathVariable String tableId) {
        personalService.addFavorite(WorkspaceContext.requireWorkspaceId(), actor(), tableId);
        return Result.success(true);
    }

    @Operation(summary = "取消收藏资产")
    @DeleteMapping("/tables/{tableId}/favorites")
    public Result<Boolean> removeFavorite(@PathVariable String tableId) {
        personalService.removeFavorite(WorkspaceContext.requireWorkspaceId(), actor(), tableId);
        return Result.success(true);
    }

    @Operation(summary = "记录一次资产访问")
    @PostMapping("/tables/{tableId}/views")
    public Result<Boolean> recordView(@PathVariable String tableId) {
        personalService.recordView(WorkspaceContext.requireWorkspaceId(), actor(), tableId);
        return Result.success(true);
    }

    @Operation(summary = "我的收藏")
    @GetMapping("/favorites")
    public Result<PagingData<DataMapTableSummaryVO>> favorites(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(
                personalService.favorites(WorkspaceContext.requireWorkspaceId(), actor(), pageNo, pageSize));
    }

    @Operation(summary = "我最近访问")
    @GetMapping("/recent")
    public Result<PagingData<DataMapTableSummaryVO>> recent(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(personalService.recent(WorkspaceContext.requireWorkspaceId(), actor(), pageNo, pageSize));
    }

    @Operation(summary = "查询采集实例")
    @GetMapping("/crawls")
    public Result<PagingData<DataMapCrawlVO>> crawls(
            @RequestParam(required = false) String datasourceId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) DataMapCrawlStatus status,
            @RequestParam(required = false) DataMapCrawlTriggerType triggerType,
            @RequestParam(required = false) Boolean failed,
            @RequestParam(required = false) @Min(1) @Max(365) Integer days,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(crawlService.list(
                WorkspaceContext.requireWorkspaceId(),
                datasourceId,
                taskId,
                status,
                triggerType,
                failed,
                days,
                pageNo,
                pageSize));
    }

    @Operation(summary = "采集健康汇总")
    @GetMapping("/crawls/summary")
    public Result<DataMapCrawlSummaryVO> crawlSummary(
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) Integer hours) {
        return Result.success(crawlService.summary(WorkspaceContext.requireWorkspaceId(), hours));
    }

    @Operation(summary = "查询采集任务列表")
    @GetMapping("/tasks")
    public Result<PagingData<DataMapCrawlTaskVO>> tasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DataMapCrawlTaskStatus status,
            @RequestParam(required = false) String datasourceId,
            @RequestParam(required = false) Boolean abnormal,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(taskService.list(
                WorkspaceContext.requireWorkspaceId(), keyword, status, datasourceId, abnormal, pageNo, pageSize));
    }

    @Operation(summary = "采集任务详情")
    @GetMapping("/tasks/{taskId}")
    public Result<DataMapCrawlTaskVO> task(@PathVariable String taskId) {
        return Result.success(taskService.detail(WorkspaceContext.requireWorkspaceId(), taskId));
    }

    @Operation(summary = "预览排除规则命中的表")
    @PostMapping("/tasks/preview-exclude")
    public Result<DataMapCrawlExcludePreviewVO> previewExclude(
            @Valid @RequestBody DataMapCrawlExcludePreviewDTO input) {
        return Result.success(excludePreviewService.preview(WorkspaceContext.requireWorkspaceId(), input));
    }

    @Operation(summary = "预览采集范围的表数量")
    @PostMapping("/tasks/preview-scope")
    public Result<DataMapCrawlScopePreviewVO> previewScope(@Valid @RequestBody DataMapCrawlScopePreviewDTO input) {
        return Result.success(excludePreviewService.previewScope(WorkspaceContext.requireWorkspaceId(), input));
    }

    @Operation(summary = "预览 Cron 的未来触发时间")
    @PostMapping("/tasks/preview-schedule")
    public Result<DataMapCrawlSchedulePreviewVO> previewSchedule(
            @Valid @RequestBody DataMapCrawlSchedulePreviewDTO input) {
        return Result.success(
                new DataMapCrawlSchedulePreviewVO(taskService.previewSchedule(input.getCronExpression())));
    }

    @Operation(summary = "创建采集任务")
    @PostMapping("/tasks")
    public Result<DataMapCrawlTaskVO> createTask(@Valid @RequestBody DataMapCrawlTaskSaveDTO input) {
        return Result.success(taskService.create(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "更新采集任务")
    @PutMapping("/tasks/{taskId}")
    public Result<DataMapCrawlTaskVO> updateTask(
            @PathVariable String taskId, @Valid @RequestBody DataMapCrawlTaskSaveDTO input) {
        return Result.success(taskService.update(WorkspaceContext.requireWorkspaceId(), actor(), taskId, input));
    }

    @Operation(summary = "上线或下线采集任务")
    @PutMapping("/tasks/{taskId}/status")
    public Result<DataMapCrawlTaskVO> changeTaskStatus(
            @PathVariable String taskId, @Valid @RequestBody DataMapCrawlTaskStatusDTO input) {
        return Result.success(
                taskService.changeStatus(WorkspaceContext.requireWorkspaceId(), actor(), taskId, input.getStatus()));
    }

    @Operation(summary = "删除采集任务")
    @DeleteMapping("/tasks/{taskId}")
    public Result<Boolean> deleteTask(@PathVariable String taskId) {
        taskService.delete(WorkspaceContext.requireWorkspaceId(), taskId);
        return Result.success(true);
    }

    @Operation(summary = "手动执行采集任务")
    @PostMapping("/tasks/{taskId}/run")
    public Result<DataMapCrawlVO> runTask(@PathVariable String taskId) {
        return Result.success(taskService.run(WorkspaceContext.requireWorkspaceId(), actor(), taskId));
    }

    @Operation(summary = "重试采集任务实例的失败表")
    @PostMapping("/tasks/{taskId}/runs/{crawlId}/retry")
    public Result<DataMapCrawlVO> retryTaskRun(@PathVariable String taskId, @PathVariable String crawlId) {
        return Result.success(taskService.retry(WorkspaceContext.requireWorkspaceId(), actor(), taskId, crawlId));
    }

    @Operation(summary = "提交采集任务")
    @PostMapping("/crawls")
    public Result<DataMapCrawlVO> submitCrawl(@Valid @RequestBody DataMapCrawlSubmitDTO input) {
        return Result.success(crawlService.submit(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "采集任务详情")
    @GetMapping("/crawls/{crawlId}")
    public Result<DataMapCrawlDetailVO> crawl(@PathVariable String crawlId) {
        return Result.success(crawlService.detail(WorkspaceContext.requireWorkspaceId(), crawlId));
    }

    private String actor() {
        return authentication.getLoginUserId();
    }
}
