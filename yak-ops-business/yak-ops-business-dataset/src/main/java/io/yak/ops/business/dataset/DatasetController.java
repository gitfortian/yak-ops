package io.yak.ops.business.dataset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 数据集接口")
@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

  private final DatasetService service;

  public DatasetController(DatasetService service) {
    this.service = service;
  }

  @Operation(summary = "查询 Dataset 列表")
  @GetMapping
  public Result<List<Dataset>> list() {
    return Result.success(service.list());
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
}
