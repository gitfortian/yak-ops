package io.yak.ops.business.lineage.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageDirection;
import io.yak.ops.business.lineage.LineageService;
import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterAssetRequest;
import io.yak.ops.business.lineage.controller.v1.dto.LineageRequests.RegisterRelationRequest;
import io.yak.ops.business.lineage.controller.v1.mapper.LineageRequestMapper;
import io.yak.ops.business.lineage.controller.v1.mapper.LineageViewMapper;
import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.AssetView;
import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.GraphView;
import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.RelationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for metadata lineage APIs. */
@Tag(name = "数据血缘接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lineage")
public class LineageController {

  private final LineageService lineageService;
  private final LineageRequestMapper requestMapper;
  private final LineageViewMapper viewMapper;

  @Operation(summary = "注册或更新元数据资产")
  @PostMapping("/assets")
  public Result<AssetView> registerAsset(@Valid @RequestBody RegisterAssetRequest request) {
    return Result.success(viewMapper.asset(lineageService.registerAsset(requestMapper.asset(request))));
  }

  @Operation(summary = "注册或更新元数据关系")
  @PostMapping("/relations")
  public Result<RelationView> registerRelation(
      @Valid @RequestBody RegisterRelationRequest request) {
    return Result.success(
        viewMapper.relation(lineageService.registerRelation(requestMapper.relation(request))));
  }

  @Operation(summary = "搜索血缘资产")
  @GetMapping("/assets")
  public Result<List<AssetView>> searchAssets(
      @RequestParam(value = "keyword", required = false) @Size(max = 200) String keyword,
      @RequestParam(value = "assetType", required = false) LineageAssetType assetType,
      @RequestParam(value = "limit", defaultValue = "30")
      @Min(1) @Max(LineageService.MAX_ASSET_SEARCH_LIMIT) int limit) {
    return Result.success(
        lineageService.searchAssets(keyword, assetType, limit).stream().map(viewMapper::asset).toList());
  }

  @Operation(summary = "查询血缘资产")
  @GetMapping("/assets/{assetId}")
  public Result<AssetView> getAsset(@PathVariable("assetId") long assetId) {
    return Result.success(viewMapper.asset(lineageService.getAsset(assetId)));
  }

  @Operation(summary = "按稳定 assetKey 查询血缘资产")
  @GetMapping("/assets/by-key")
  public Result<AssetView> getAssetByKey(@RequestParam("assetKey") String assetKey) {
    return Result.success(viewMapper.asset(lineageService.getAssetByKey(assetKey)));
  }

  @Operation(summary = "查询上游血缘")
  @GetMapping("/assets/{assetId}/upstream")
  public Result<GraphView> upstream(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "depth", defaultValue = "1")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(viewMapper.graph(lineageService.upstream(assetId, depth)));
  }

  @Operation(summary = "查询下游血缘")
  @GetMapping("/assets/{assetId}/downstream")
  public Result<GraphView> downstream(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "depth", defaultValue = "1")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(viewMapper.graph(lineageService.downstream(assetId, depth)));
  }

  @Operation(summary = "查询指定方向的多跳血缘图")
  @GetMapping("/assets/{assetId}/graph")
  public Result<GraphView> graph(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "direction", defaultValue = "BOTH") LineageDirection direction,
      @RequestParam(value = "depth", defaultValue = "3")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(viewMapper.graph(lineageService.graph(assetId, direction, depth)));
  }
}
