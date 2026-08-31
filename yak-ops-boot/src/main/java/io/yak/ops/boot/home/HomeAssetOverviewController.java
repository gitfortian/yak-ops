package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.ops.boot.home.HomeAssetOverviewService.OverviewResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据资产与血缘总览接口。 */
@RestController
@RequestMapping("/api/v1/home/assets")
@ProjectScope
public class HomeAssetOverviewController {

  private final HomeAssetOverviewService service;

  public HomeAssetOverviewController(HomeAssetOverviewService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview() {
    return Result.success(service.overview());
  }
}
