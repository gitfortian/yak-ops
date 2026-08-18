package io.yak.ops.business.dataset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 数据集接口")
@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

  private final DatasetService service;
  private final DatasetQueryService queryService;

  public DatasetController(DatasetService service, DatasetQueryService queryService) {
    this.service = service;
    this.queryService = queryService;
  }

  @Operation(summary = "查询 Dataset 列表")
  @GetMapping
  public Result<List<Dataset>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "查询最近的 Dataset SQL 性能诊断记录")
  @GetMapping("/query-performance")
  public Result<List<DatasetQueryPerformance>> queryPerformance(
      @RequestParam(value = "datasetIds", required = false) List<Long> datasetIds,
      @RequestParam(value = "limit", defaultValue = "100") int limit) {
    Set<Long> filters = datasetIds == null ? Set.of() : new HashSet<>(datasetIds);
    return Result.success(queryService.recentPerformance(filters, limit));
  }

  @Operation(summary = "查询 Dataset 详情、当前版本与版本历史")
  @GetMapping("/{datasetId}")
  public Result<DatasetDetail> get(@PathVariable("datasetId") long datasetId) {
    return Result.success(service.get(datasetId));
  }

  @Operation(summary = "把 ONLINE SQL TaskAsset 的当前不可变版本发布为 Dataset")
  @PostMapping
  public Result<DatasetDetail> publish(@Valid @RequestBody PublishDatasetRequest request) {
    return Result.success(service.publish(new DatasetService.PublishCommand(
        request.sourceTaskAssetId(),
        request.name(),
        request.description(),
        toFieldSpecs(request.fields()))));
  }

  @Operation(summary = "把来源 TaskAsset 的当前版本发布为新的 DatasetVersion")
  @PostMapping("/{datasetId}/versions")
  public Result<DatasetDetail> createVersion(
      @PathVariable("datasetId") long datasetId,
      @Valid @RequestBody(required = false) CreateDatasetVersionRequest request) {
    return Result.success(service.createVersion(
        datasetId,
        request == null ? List.of() : toFieldSpecs(request.fields())));
  }

  @Operation(summary = "通过 Dataset Query Runtime 查询当前或指定不可变版本")
  @PostMapping("/{datasetId}/query")
  public Result<DatasetQueryResult> query(
      @PathVariable("datasetId") long datasetId,
      @Valid @RequestBody(required = false) QueryDatasetRequest request) {
    return Result.success(queryService.query(datasetId, toQueryRequest(request)));
  }

  @Operation(summary = "上线 Dataset")
  @PostMapping("/{datasetId}/online")
  public Result<DatasetDetail> online(@PathVariable("datasetId") long datasetId) {
    return Result.success(service.online(datasetId));
  }

  @Operation(summary = "下线 Dataset")
  @PostMapping("/{datasetId}/offline")
  public Result<DatasetDetail> offline(@PathVariable("datasetId") long datasetId) {
    return Result.success(service.offline(datasetId));
  }

  private static DatasetQueryRequest toQueryRequest(QueryDatasetRequest request) {
    if (request == null) return null;
    List<DatasetMetricBinding> metrics = request.metrics() == null ? List.of() : request.metrics().stream()
        .map(value -> new DatasetMetricBinding(value.fieldId(), value.aggregation()))
        .toList();
    List<DatasetFilter> filters = request.filters() == null ? List.of() : request.filters().stream()
        .map(value -> new DatasetFilter(value.fieldId(), value.operator(), value.value(), value.values()))
        .toList();
    List<DatasetSort> sorts = request.sorts() == null ? List.of() : request.sorts().stream()
        .map(value -> new DatasetSort(value.fieldId(), value.aggregation(), value.direction()))
        .toList();
    return new DatasetQueryRequest(
        request.versionNo(),
        request.dimensions(),
        metrics,
        filters,
        sorts,
        request.limit(),
        request.timeoutSeconds());
  }

  private static List<DatasetService.FieldSpec> toFieldSpecs(List<DatasetFieldRequest> fields) {
    if (fields == null || fields.isEmpty()) return List.of();
    return fields.stream()
        .map(field -> new DatasetService.FieldSpec(
            field.fieldId(),
            field.physicalName(),
            field.displayName(),
            field.dataType(),
            field.nullable(),
            field.description(),
            field.defaultRole()))
        .toList();
  }

  public record PublishDatasetRequest(
      @NotNull Long sourceTaskAssetId,
      @Size(max = 200) String name,
      @Size(max = 2000) String description,
      List<@Valid DatasetFieldRequest> fields) {
  }

  public record CreateDatasetVersionRequest(List<@Valid DatasetFieldRequest> fields) {
  }

  public record DatasetFieldRequest(
      @Size(max = 64) String fieldId,
      @NotNull @Size(max = 128) String physicalName,
      @Size(max = 200) String displayName,
      DatasetFieldDataType dataType,
      boolean nullable,
      @Size(max = 1000) String description,
      DatasetFieldRole defaultRole) {
  }

  public record QueryDatasetRequest(
      @Min(1) Integer versionNo,
      List<@Size(max = 64) String> dimensions,
      List<@Valid QueryMetricRequest> metrics,
      List<@Valid QueryFilterRequest> filters,
      List<@Valid QuerySortRequest> sorts,
      @Min(1) @Max(1000) Integer limit,
      @Min(1) @Max(120) Integer timeoutSeconds) {
  }

  public record QueryMetricRequest(
      @NotNull @Size(max = 64) String fieldId,
      @NotNull DatasetAggregation aggregation) {
  }

  public record QueryFilterRequest(
      @NotNull @Size(max = 64) String fieldId,
      @NotNull DatasetFilterOperator operator,
      Object value,
      @Size(max = 100) List<Object> values) {
  }

  public record QuerySortRequest(
      @NotNull @Size(max = 64) String fieldId,
      DatasetAggregation aggregation,
      DatasetSortDirection direction) {
  }
}
