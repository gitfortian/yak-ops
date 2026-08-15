package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.development.service.DevelopmentDataServiceService;
import io.yak.ops.business.development.service.DevelopmentDataServiceService.PublishCommand;
import io.yak.ops.business.development.service.DevelopmentDataServiceService.ReleaseDataServiceState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Release-center shortcut for the explicit "publish as Data Service" transition. */
@Tag(name = "数据开发发布中心 Data Service 接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequestMapping("/api/v1/data-development/releases")
public class DevelopmentDataServiceController {

  private final DevelopmentDataServiceService service;

  public DevelopmentDataServiceController(DevelopmentDataServiceService service) {
    this.service = service;
  }

  @Operation(summary = "查询 SQL 发布任务关联的数据服务状态")
  @GetMapping("/{assetId}/data-service")
  public Result<ReleaseDataServiceState> getDataService(@PathVariable("assetId") long assetId) {
    return Result.success(service.state(assetId));
  }

  @Operation(summary = "将已上线 SQL 当前版本发布或更新为数据服务")
  @PostMapping("/{assetId}/data-service")
  public Result<ApiView> publishAsDataService(
      @PathVariable("assetId") long assetId,
      @Valid @RequestBody(required = false) ReleaseDataServiceRequest request) {
    return Result.success(service.publish(
        assetId,
        request == null
            ? null
            : new PublishCommand(
                request.name(),
                request.path(),
                request.maxRows(),
                request.timeoutSeconds(),
                request.enabled(),
                request.description())));
  }

  public record ReleaseDataServiceRequest(
      @Size(max = 200) String name,
      @Size(max = 255) String path,
      @Min(1) @Max(10_000) Integer maxRows,
      @Min(1) @Max(3_600) Integer timeoutSeconds,
      Boolean enabled,
      @Size(max = 2000) String description) {}
}
