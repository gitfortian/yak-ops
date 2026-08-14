package io.yak.ops.business.dataset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Release-center shortcut for the explicit "publish as Dataset" domain transition. */
@Tag(name = "数据开发发布中心 Dataset 接口")
@RestController
@RequestMapping("/api/v1/data-development/releases")
public class DatasetReleaseController {

  private final DatasetService service;

  public DatasetReleaseController(DatasetService service) {
    this.service = service;
  }

  @Operation(summary = "查询已发布 SQL 任务关联的 Dataset 状态")
  @GetMapping("/{assetId}/dataset")
  public Result<ReleaseDatasetState> getReleaseDataset(@PathVariable("assetId") long assetId) {
    Optional<DatasetDetail> detail = service.findBySourceTaskAssetId(assetId);
    return Result.success(new ReleaseDatasetState(detail.isPresent(), detail.orElse(null)));
  }

  @Operation(summary = "预览已上线 SQL 当前版本的 Dataset 输出字段")
  @PostMapping("/{assetId}/dataset/preview")
  public Result<List<ReleaseDatasetField>> previewDataset(@PathVariable("assetId") long assetId) {
    return Result.success(service.previewReleaseFields(assetId).stream()
        .map(DatasetReleaseController::toReleaseField)
        .toList());
  }

  @Operation(summary = "将已上线 SQL 任务发布或更新为 Dataset")
  @PostMapping("/{assetId}/dataset")
  public Result<DatasetDetail> publishAsDataset(
      @PathVariable("assetId") long assetId,
      @Valid @RequestBody(required = false) ReleaseDatasetRequest request) {
    String name = request == null ? null : request.name();
    String description = request == null ? null : request.description();
    List<DatasetService.FieldSpec> fields = request == null || request.fields() == null
        ? List.of()
        : request.fields().stream().map(DatasetReleaseController::toFieldSpec).toList();
    return Result.success(service.publishFromRelease(
        new DatasetService.PublishCommand(assetId, name, description, fields)));
  }

  private static DatasetService.FieldSpec toFieldSpec(ReleaseDatasetField field) {
    return new DatasetService.FieldSpec(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType(),
        field.nullable(),
        field.description(),
        field.defaultRole());
  }

  private static ReleaseDatasetField toReleaseField(DatasetService.FieldSpec field) {
    return new ReleaseDatasetField(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType(),
        field.nullable(),
        field.description(),
        field.defaultRole());
  }

  public record ReleaseDatasetRequest(
      @Size(max = 200) String name,
      @Size(max = 2000) String description,
      List<@Valid ReleaseDatasetField> fields) {
  }

  public record ReleaseDatasetField(
      @Size(max = 64) String fieldId,
      @NotBlank @Size(max = 128) String physicalName,
      @Size(max = 200) String displayName,
      DatasetFieldDataType dataType,
      boolean nullable,
      @Size(max = 1000) String description,
      DatasetFieldRole defaultRole) {
  }

  public record ReleaseDatasetState(
      boolean published,
      DatasetDetail detail) {
  }
}
