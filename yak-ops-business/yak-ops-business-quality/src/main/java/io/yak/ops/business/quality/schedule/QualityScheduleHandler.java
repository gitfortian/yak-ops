package io.yak.ops.business.quality.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.business.quality.service.QualityExecutionService;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import org.springframework.stereotype.Component;

/** Yak Schedule 数据质量 Handler：统一进入 QualityExecutionService。 */
@ConditionalOnQualityEnabled
@Component(QualityScheduleEngineBridge.HANDLER)
public class QualityScheduleHandler implements ScheduleHandler {
  private final QualityRepository repository;
  private final QualityExecutionService executionService;
  private final QualityScheduleEngineBridge engine;
  private final QualityScheduleLifecycle lifecycle;

  public QualityScheduleHandler(
      QualityRepository repository,
      QualityExecutionService executionService,
      QualityScheduleEngineBridge engine,
      QualityScheduleLifecycle lifecycle) {
    this.repository = repository;
    this.executionService = executionService;
    this.engine = engine;
    this.lifecycle = lifecycle;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long monitorId = context.requiredLong("monitorId");
    Monitor monitor = repository.findMonitor(monitorId).orElse(null);
    if (monitor == null) {
      engine.deleteIfPresent(monitorId);
      return ScheduleExecutionResult.accepted(null, "质量监控已删除，清理残留调度计划");
    }

    MonitorSettings settings = repository.findMonitorSettings(monitorId);
    if (!monitor.enabled() || settings.runMode() != RunMode.SCHEDULE) {
      engine.pauseIfPresent(monitorId);
      lifecycle.refreshRuntimeState(monitorId);
      return ScheduleExecutionResult.accepted(null, "质量监控未启用调度，本次触发忽略");
    }

    try {
      QualityMonitorVO.Run run = executionService.runScheduled(monitorId);
      return ScheduleExecutionResult.accepted(run.executionNo(), "质量检查已提交执行");
    } finally {
      lifecycle.refreshRuntimeState(monitorId);
    }
  }
}
