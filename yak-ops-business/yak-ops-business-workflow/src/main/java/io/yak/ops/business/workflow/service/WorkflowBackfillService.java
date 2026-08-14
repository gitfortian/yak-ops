package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.persistence.support.WorkflowJsonCodec;
import io.yak.ops.business.workflow.service.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.service.WorkflowBackfillPlanner.Plan;
import io.yak.ops.common.bean.dto.workflow.WorkflowBackfillCreateDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillPreviewVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Backfill 批次创建、预览、取消与 Trigger Ledger 分发。 */
@Service
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillService {
  private static final Logger log = LoggerFactory.getLogger(WorkflowBackfillService.class);
  private static final Set<String> STRATEGIES = Set.of("SERIAL_WAIT", "PARALLEL");

  private final WorkflowScheduleQuery schedules;
  private final WorkflowDefinitionService definitions;
  private final WorkflowBackfillPlanner planner;
  private final WorkflowBackfillDao dao;
  private final WorkflowBackfillQuery query;
  private final WorkflowScheduleTriggerDao triggers;
  private final WorkflowScheduleTriggerAdmission admission;
  private final WorkflowScheduleTriggerCoordinator coordinator;
  private final WorkflowJsonCodec json;

  public WorkflowBackfillService(
      WorkflowScheduleQuery schedules,
      WorkflowDefinitionService definitions,
      WorkflowBackfillPlanner planner,
      WorkflowBackfillDao dao,
      WorkflowBackfillQuery query,
      WorkflowScheduleTriggerDao triggers,
      WorkflowScheduleTriggerAdmission admission,
      WorkflowScheduleTriggerCoordinator coordinator,
      WorkflowJsonCodec json) {
    this.schedules = schedules;
    this.definitions = definitions;
    this.planner = planner;
    this.dao = dao;
    this.query = query;
    this.triggers = triggers;
    this.admission = admission;
    this.coordinator = coordinator;
    this.json = json;
  }

  public WorkflowBackfillPreviewVO preview(WorkflowBackfillCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("Backfill 参数不能为空");
    WorkflowSchedulePO schedule = schedules.require(request.scheduleId());
    Plan plan = planner.plan(
        schedule.getCronExpression(),
        schedule.getTimezone(),
        request.startBusinessDate(),
        request.endBusinessDate());
    List<WorkflowBackfillPreviewVO.OccurrenceVO> visible = plan.occurrences().stream()
        .limit(100)
        .map(value -> new WorkflowBackfillPreviewVO.OccurrenceVO(
            value.businessDate(), value.scheduleInstant(), value.scheduleTime()))
        .toList();
    return new WorkflowBackfillPreviewVO(
        schedule.getId(),
        plan.cronExpression(),
        plan.timezone(),
        request.startBusinessDate(),
        request.endBusinessDate(),
        plan.occurrences().size(),
        plan.occurrences().size() > visible.size(),
        visible);
  }

  public WorkflowBackfillVO create(WorkflowBackfillCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("Backfill 参数不能为空");
    WorkflowSchedulePO schedule = schedules.require(request.scheduleId());
    var workflow = definitions.get(schedule.getWorkflowId());
    if (!"ONLINE".equals(workflow.status()) || workflow.activeVersionId() == null) {
      throw new IllegalStateException("工作流需要先发布并上线，才能执行 Backfill");
    }

    String strategy = strategy(request.executionStrategy());
    Plan plan = planner.plan(
        schedule.getCronExpression(),
        schedule.getTimezone(),
        request.startBusinessDate(),
        request.endBusinessDate());

    Instant now = Instant.now();
    WorkflowBackfillPO backfill = new WorkflowBackfillPO();
    backfill.setId("workflow-backfill-" + UUID.randomUUID());
    backfill.setWorkflowId(schedule.getWorkflowId());
    backfill.setWorkflowVersionId(workflow.activeVersionId());
    backfill.setWorkflowVersionNo(workflow.activeVersionNo());
    backfill.setScheduleId(schedule.getId());
    backfill.setScheduleName(schedule.getName());
    backfill.setName(name(request, schedule));
    backfill.setStatus("RUNNING");
    backfill.setStartBusinessDate(request.startBusinessDate());
    backfill.setEndBusinessDate(request.endBusinessDate());
    backfill.setCronExpression(plan.cronExpression());
    backfill.setTimezone(plan.timezone());
    backfill.setExecutionStrategy(strategy);
    backfill.setScheduleInputJson(schedule.getInputJson());
    backfill.setInputJson(json.write(request.input()));
    backfill.setTotalCount(plan.occurrences().size());
    backfill.setCreateTime(now);
    backfill.setUpdateTime(now);
    if (dao.insert(backfill) != 1) throw new IllegalStateException("创建 Backfill 批次失败");

    for (Occurrence occurrence : plan.occurrences()) {
      try {
        coordinator.submitBackfill(backfill, occurrence);
      } catch (RuntimeException exception) {
        // 单个逻辑日期启动失败已经写入 Ledger；批次继续创建其余日期，最终由汇总状态反映失败。
        log.warn(
            "[workflow-backfill] dispatch failed backfill={}, businessDate={}, scheduleTime={}, message={}",
            backfill.getId(),
            occurrence.businessDate(),
            occurrence.scheduleTime(),
            exception.getMessage());
      }
    }
    return query.get(backfill.getId());
  }

  /** 取消只阻止尚未启动的补数 Trigger，已经 RUNNING 的 WorkflowExecution 继续完成。 */
  public WorkflowBackfillVO cancel(String id) {
    WorkflowBackfillPO backfill = query.require(id);
    if (!"CANCELED".equals(backfill.getStatus())) {
      backfill.setStatus("CANCELED");
      backfill.setUpdateTime(Instant.now());
      if (dao.update(backfill) != 1) throw new IllegalStateException("取消 Backfill 批次失败：" + id);
    }
    for (WorkflowScheduleTriggerPO trigger : triggers.selectByBackfillId(backfill.getId())) {
      if ("RECEIVED".equals(trigger.getStatus()) || "WAITING".equals(trigger.getStatus())) {
        admission.skip(trigger, "Backfill 批次已取消，尚未启动的 Trigger 跳过");
      }
    }
    return query.get(backfill.getId());
  }

  private String strategy(String value) {
    String normalized = value == null || value.isBlank()
        ? "SERIAL_WAIT"
        : value.trim().toUpperCase(Locale.ROOT);
    if (!STRATEGIES.contains(normalized)) {
      throw new IllegalArgumentException("Backfill 仅支持 SERIAL_WAIT 或 PARALLEL：" + normalized);
    }
    return normalized;
  }

  private String name(WorkflowBackfillCreateDTO request, WorkflowSchedulePO schedule) {
    if (request.name() != null && !request.name().isBlank()) {
      String value = request.name().trim();
      if (value.length() > 120) throw new IllegalArgumentException("Backfill 名称不能超过 120 个字符");
      return value;
    }
    return schedule.getName() + " · " + request.startBusinessDate() + " ~ " + request.endBusinessDate();
  }
}
