package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.boot.home.HomeQualityOverviewService.OverviewResponse;
import io.yak.ops.business.quality.QualityPermissionCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据质量总览接口。 */
@RestController
@RequestMapping("/api/v1/home/quality")
@RequiresPermission(QualityPermissionCode.EXECUTION_READ)
public class HomeQualityOverviewController {

  private final HomeQualityOverviewService service;

  public HomeQualityOverviewController(HomeQualityOverviewService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview() {
    return Result.success(service.overview());
  }
}
