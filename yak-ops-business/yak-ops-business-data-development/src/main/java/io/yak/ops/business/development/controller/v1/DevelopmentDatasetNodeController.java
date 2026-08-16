package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.FieldDraft;
import io.yak.ops.business.development.service.DevelopmentDatasetNodeService;
import io.yak.ops.business.development.service.DevelopmentDatasetNodeService.DatasetNodeContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dataset Node editor API. Workflow topology remains owned by the workflow module. */
@Tag(name = "数据开发 Dataset Node 接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
public class DevelopmentDatasetNodeController {

  private final DevelopmentDatasetNodeService service;

  public DevelopmentDatasetNodeController(DevelopmentDatasetNodeService service) {
    this.service = service;
  }

  @Operation(summary = "查询 Dataset Node、可选 SQL 来源与 Dataset 资产")
  @GetMapping("/{nodeId}/dataset")
  public Result<DatasetNodeContext> get(@PathVariable("nodeId") long nodeId) {
    return Result.success(service.get(nodeId));
  }

  @Operation(summary = "基于选中的已发布 SQL Revision 发现 Dataset 字段")
  @PostMapping("/{nodeId}/dataset/preview")
  public Result<List<FieldDraft>> preview(
      @PathVariable("nodeId") long nodeId,
      @Valid @RequestBody DatasetSourceRequest request) {
    return Result.success(service.preview(nodeId, request.sourceTaskAssetId()));
  }

  @Operation(summary = "保存 Dataset Node 并冻结选中 SQL 的当前 Revision")
  @PutMapping("/{nodeId}/dataset")
  public Result<DatasetNodeContext> save(
      @PathVariable("nodeId") long nodeId,
      @Valid @RequestBody SaveDatasetNodeRequest request) {
    List<FieldDraft> fields = request.fields() == null
        ? List.of()
        : request.fields().stream().map(DevelopmentDatasetNodeController::toFieldDraft).toList();
    return Result.success(service.save(
        nodeId,
        request.sourceTaskAssetId(),
        request.description(),
        fields));
  }

  private static FieldDraft toFieldDraft(DatasetFieldRequest field) {
    return new FieldDraft(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType(),
        field.nullable(),
        field.description(),
        field.defaultRole());
  }

  public record DatasetSourceRequest(
      @NotNull @Min(1) Long sourceTaskAssetId) {
  }

  public record SaveDatasetNodeRequest(
      @NotNull @Min(1) Long sourceTaskAssetId,
      @Size(max = 2000) String description,
      List<@Valid DatasetFieldRequest> fields) {
  }

  public record DatasetFieldRequest(
      @Size(max = 64) String fieldId,
      @NotBlank @Size(max = 128) String physicalName,
      @Size(max = 200) String displayName,
      @NotBlank @Size(max = 32) String dataType,
      boolean nullable,
      @Size(max = 1000) String description,
      @NotBlank @Size(max = 32) String defaultRole) {
  }
}
