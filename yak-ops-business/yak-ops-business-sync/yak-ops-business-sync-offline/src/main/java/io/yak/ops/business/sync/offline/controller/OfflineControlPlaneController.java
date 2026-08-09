package io.yak.ops.business.sync.offline.controller;

import io.yak.framework.common.Result;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.service.OfflineJobExecutionService;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionEventVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 离线同步执行事件查询接口。 */
@ConditionalOnOfflineSyncEnabled
@RestController
@RequestMapping("/api/v1/job/batch-control")
@RequiredArgsConstructor
public class OfflineControlPlaneController {
  private final OfflineJobExecutionService service;

  @GetMapping("/executions/{executionId}/events")
  public Result<List<OfflineExecutionEventVO>> events(@PathVariable Long executionId) {
    return Result.success(service.events(executionId));
  }
}
