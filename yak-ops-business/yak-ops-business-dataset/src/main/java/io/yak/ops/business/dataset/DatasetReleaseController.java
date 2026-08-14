package io.yak.ops.business.dataset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
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

  @Operation(summary = "将已上线 SQL 任务的当前版本发布为 Dataset")
  @PostMapping("/{assetId}/dataset")
  public Result<DatasetDetail> publishAsDataset(
      @PathVariable("assetId") long assetId,
      @Valid @RequestBody(required = false) ReleaseDatasetRequest request) {
    String name = request == null ? null : request.name();
    String description = request == null ? null : request.description();
    return Result.success(service.publish(
        new DatasetService.PublishCommand(assetId, name, description, List.of())));
  }

  public record ReleaseDatasetRequest(
      @Size(max = 200) String name,
      @Size(max = 2000) String description) {
  }
}
