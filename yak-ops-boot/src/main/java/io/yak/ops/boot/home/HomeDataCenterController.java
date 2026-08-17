package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.ops.boot.home.HomeDataCenterService.OverviewResponse;
import io.yak.ops.boot.home.HomeDataCenterService.RecentResponse;
import io.yak.ops.boot.home.HomeDataCenterService.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据中心接口。 */
@RestController
@RequestMapping("/api/v1/home/data-center")
@RequiredArgsConstructor
public class HomeDataCenterController {

  private final HomeDataCenterService service;

  @GetMapping("/overview")
  public Result<OverviewResponse> overview(
      @RequestParam(defaultValue = "7d") String period) {
    return Result.success(service.overview(period));
  }

  @GetMapping("/recent")
  public Result<RecentResponse> recent() {
    return Result.success(service.recent());
  }

  @GetMapping("/schedule")
  public Result<ScheduleResponse> schedules(
      @RequestParam(defaultValue = "7d") String period) {
    return Result.success(service.schedules(period));
  }
}
