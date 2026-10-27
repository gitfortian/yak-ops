package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datamap.catalog.DataMapCatalogService;
import io.yak.ops.business.datamap.crawl.DataMapCrawlService;
import io.yak.ops.business.datamap.personal.DataMapPersonalService;
import io.yak.ops.business.datamap.preview.DataMapPreviewService;
import io.yak.ops.business.datamap.tagging.DataMapTagService;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCuratedUpdateDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTableQueryDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTagAssignmentDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTagInputDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapPreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableChangeVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTagVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
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
    private DataMapPreviewService previewService;

    @Resource
    private DataMapPersonalService personalService;

    @Resource
    private DataMapTagService tagService;

    @Resource
    private AuthenticationManager authentication;

    @Operation(summary = "数据地图总览")
    @GetMapping("/overview")
    public Result<DataMapOverviewVO> overview() {
        return Result.success(catalogService.overview(WorkspaceContext.requireWorkspaceId(), actor()));
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

    @Operation(summary = "替换资产标签")
    @PutMapping("/tables/{tableId}/tags")
    public Result<Boolean> assignTags(@PathVariable String tableId, @Valid @RequestBody DataMapTagAssignmentDTO input) {
        tagService.assign(WorkspaceContext.requireWorkspaceId(), actor(), tableId, input.getTagIds());
        return Result.success(true);
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

    @Operation(summary = "查询工作空间标签")
    @GetMapping("/tags")
    public Result<List<DataMapTagVO>> tags() {
        return Result.success(tagService.list(WorkspaceContext.requireWorkspaceId()));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public Result<DataMapTagVO> createTag(@Valid @RequestBody DataMapTagInputDTO input) {
        return Result.success(tagService.create(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "重命名标签")
    @PutMapping("/tags/{tagId}")
    public Result<DataMapTagVO> renameTag(@PathVariable String tagId, @Valid @RequestBody DataMapTagInputDTO input) {
        return Result.success(tagService.rename(WorkspaceContext.requireWorkspaceId(), actor(), tagId, input));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{tagId}")
    public Result<Boolean> deleteTag(@PathVariable String tagId) {
        tagService.delete(WorkspaceContext.requireWorkspaceId(), tagId);
        return Result.success(true);
    }

    @Operation(summary = "查询采集任务")
    @GetMapping("/crawls")
    public Result<PagingData<DataMapCrawlVO>> crawls(
            @RequestParam(required = false) String datasourceId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(crawlService.list(WorkspaceContext.requireWorkspaceId(), datasourceId, pageNo, pageSize));
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
