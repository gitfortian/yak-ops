package io.yak.ops.boot.home;

import io.yak.framework.common.Result;
import io.yak.ops.boot.home.HomeScheduleCenterService.CalendarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 首页调度中心接口。 */
@RestController
@RequestMapping("/api/v1/home/schedule-center")
@RequiredArgsConstructor
public class HomeScheduleCenterController {

  private final HomeScheduleCenterService service;

  @GetMapping("/calendar")
  public Result<CalendarResponse> calendar(
      @RequestParam(required = false) String month) {
    return Result.success(service.calendar(month));
  }
}
