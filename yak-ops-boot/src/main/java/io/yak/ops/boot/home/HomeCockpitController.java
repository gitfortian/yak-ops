package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.ops.boot.home.HomeCockpitService.CockpitResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页系统全貌与待处理事项接口。 */
@RestController
@RequestMapping("/api/v1/home/cockpit")
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
