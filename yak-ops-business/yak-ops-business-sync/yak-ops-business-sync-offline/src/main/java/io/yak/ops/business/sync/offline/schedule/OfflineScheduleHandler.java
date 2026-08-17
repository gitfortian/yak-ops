package io.yak.ops.business.sync.offline.schedule;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleHandler;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionOrchestrator;
import org.springframework.stereotype.Component;

/** Yak Schedule 离线同步 Handler：到点后统一进入现有 Offline Execution 执行链。 */
@ConditionalOnOfflineSyncEnabled
@Component(OfflineScheduleEngineBridge.HANDLER)
public class OfflineScheduleHandler implements ScheduleHandler {
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineExecutionOrchestrator orchestrator;
  private final OfflineScheduleEngineBridge engine;
  private final OfflineScheduleLifecycle lifecycle;

  public OfflineScheduleHandler(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineExecutionOrchestrator orchestrator,
      OfflineScheduleEngineBridge engine,
      OfflineScheduleLifecycle lifecycle) {
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.scheduleRepository = scheduleRepository;
    this.orchestrator = orchestrator;
    this.engine = engine;
    this.lifecycle = lifecycle;
  }

  @Override
  public ScheduleExecutionResult execute(ScheduleExecutionContext context) {
    long definitionId = context.requiredLong("definitionId");
    OfflineJobDefinition definition = definitionRepository.findById(definitionId).orElse(null);
    if (definition == null) {
      engine.deleteIfPresent(definitionId);
      return ScheduleExecutionResult.accepted(null, "离线同步任务已删除，清理残留调度计划");
    }

    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())
        || schedule == null
        || !schedule.enabled()) {
      lifecycle.sync(definitionId);
      return ScheduleExecutionResult.accepted(null, "离线同步任务未启用调度，本次触发忽略");
    }

    if (executionRepository.hasActiveExecution(definitionId)) {
      lifecycle.refreshRuntimeState(definitionId, context.actualFireTime());
      return ScheduleExecutionResult.accepted(null, "任务已有运行实例，本次调度触发跳过");
    }

    try {
      OfflineJobExecution execution = orchestrator.execute(definitionId, "SCHEDULE", null, 1);
      return ScheduleExecutionResult.accepted(
          execution.getId() == null ? null : String.valueOf(execution.getId()),
          "离线同步任务已提交 Link-Up 执行");
    } finally {
      lifecycle.refreshRuntimeState(definitionId, context.actualFireTime());
    }
  }
}
