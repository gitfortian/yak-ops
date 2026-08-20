package io.yak.ops.business.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据血缘接口")
@Validated
@RestController
@RequestMapping("/api/v1/lineage")
public class LineageController {

  private final LineageService service;

  public LineageController(LineageService service) {
    this.service = service;
  }

  @Operation(summary = "注册或更新元数据资产")
  @PostMapping("/assets")
  public Result<LineageAsset> registerAsset(@Valid @RequestBody RegisterAssetRequest request) {
    return Result.success(service.registerAsset(new LineageService.RegisterAssetCommand(
        request.assetKey(),
        request.assetType(),
        request.name(),
        request.sourceType(),
        request.sourceId(),
        request.parentAssetId(),
        request.dataSourceId(),
        request.databaseName(),
        request.schemaName(),
        request.tableName(),
        request.columnName(),
        request.properties())));
  }

  @Operation(summary = "注册或更新元数据关系")
  @PostMapping("/relations")
  public Result<LineageRelation> registerRelation(
      @Valid @RequestBody RegisterRelationRequest request) {
    return Result.success(service.registerRelation(new LineageService.RegisterRelationCommand(
        request.sourceAssetId(),
        request.targetAssetId(),
        request.relationType(),
        request.sourceType(),
        request.sourceId(),
        request.expression(),
        request.confidence(),
        request.version(),
        request.observedAt(),
        request.properties())));
  }

  @Operation(summary = "搜索血缘资产")
  @GetMapping("/assets")
  public Result<List<LineageAsset>> searchAssets(
      @RequestParam(value = "keyword", required = false) @Size(max = 200) String keyword,
      @RequestParam(value = "assetType", required = false) LineageAssetType assetType,
      @RequestParam(value = "limit", defaultValue = "30")
      @Min(1) @Max(LineageService.MAX_ASSET_SEARCH_LIMIT) int limit) {
    return Result.success(service.searchAssets(keyword, assetType, limit));
  }

  @Operation(summary = "查询血缘资产")
  @GetMapping("/assets/{assetId}")
  public Result<LineageAsset> getAsset(@PathVariable("assetId") long assetId) {
    return Result.success(service.getAsset(assetId));
  }

  @Operation(summary = "按稳定 assetKey 查询血缘资产")
  @GetMapping("/assets/by-key")
  public Result<LineageAsset> getAssetByKey(@RequestParam("assetKey") String assetKey) {
    return Result.success(service.getAssetByKey(assetKey));
  }

  @Operation(summary = "查询上游血缘")
  @GetMapping("/assets/{assetId}/upstream")
  public Result<LineageGraph> upstream(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "depth", defaultValue = "1")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(service.upstream(assetId, depth));
  }

  @Operation(summary = "查询下游血缘")
  @GetMapping("/assets/{assetId}/downstream")
  public Result<LineageGraph> downstream(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "depth", defaultValue = "1")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(service.downstream(assetId, depth));
  }

  @Operation(summary = "查询指定方向的多跳血缘图")
  @GetMapping("/assets/{assetId}/graph")
  public Result<LineageGraph> graph(
      @PathVariable("assetId") long assetId,
      @RequestParam(value = "direction", defaultValue = "BOTH") LineageDirection direction,
      @RequestParam(value = "depth", defaultValue = "3")
      @Min(1) @Max(LineageService.MAX_GRAPH_DEPTH) int depth) {
    return Result.success(service.graph(assetId, direction, depth));
  }

  public record RegisterAssetRequest(
      @NotBlank @Size(max = 512) String assetKey,
      @NotNull LineageAssetType assetType,
      @Size(max = 200) String name,
      @Size(max = 64) String sourceType,
      @Size(max = 200) String sourceId,
      Long parentAssetId,
      @Size(max = 64) String dataSourceId,
      @Size(max = 256) String databaseName,
      @Size(max = 256) String schemaName,
      @Size(max = 256) String tableName,
      @Size(max = 256) String columnName,
      JsonNode properties) {
  }

  public record RegisterRelationRequest(
      @NotNull Long sourceAssetId,
      @NotNull Long targetAssetId,
      @NotNull LineageRelationType relationType,
      @Size(max = 64) String sourceType,
      @Size(max = 200) String sourceId,
      @Size(max = 16000) String expression,
      @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
      @Size(max = 128) String version,
      Instant observedAt,
      JsonNode properties) {
  }
}
