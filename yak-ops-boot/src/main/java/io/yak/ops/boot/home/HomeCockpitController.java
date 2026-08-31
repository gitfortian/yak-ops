package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.ops.boot.home.HomeCockpitService.CockpitResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页头部摘要接口。 */
@RestController
@RequestMapping("/api/v1/home/cockpit")
@ProjectScope
public class HomeCockpitController {

  private final HomeCockpitService service;

  public HomeCockpitController(HomeCockpitService service) {
    this.service = service;
  }

  @GetMapping
  public Result<CockpitResponse> cockpit() {
    return Result.success(service.cockpit());
  }
}
