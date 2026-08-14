package io.yak.ops.business.workflow.service;

import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 工作流调度定义启停生命周期，并同步 Yak Schedule 引擎状态。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleLifecycle {
  private final WorkflowDefinitionService definitions;
  private final WorkflowScheduleQuery query;
  private final WorkflowScheduleDao dao;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;

  public WorkflowScheduleLifecycle(
      WorkflowDefinitionService definitions,
      WorkflowScheduleQuery query,
      WorkflowScheduleDao dao,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState) {
    this.definitions = definitions;
    this.query = query;
    this.dao = dao;
    this.engine = engine;
    this.runtimeState = runtimeState;
  }

  public WorkflowScheduleVO online(String id) {
    WorkflowSchedulePO value = query.require(id);
    var workflow = definitions.get(value.getWorkflowId());
    if (!"ONLINE".equals(workflow.status()) || workflow.activeVersionId() == null) {
      throw new IllegalStateException("工作流需要先发布并上线，才能启用调度");
    }
    Instant now = Instant.now();
    if (value.getEndTime() != null && !value.getEndTime().isAfter(now)) {
      throw new IllegalStateException("调度生效结束时间已过，请调整生效区间后再启用");
    }

    value.setStatus("ONLINE");
    value.setUpdateTime(now);
    try {
      ScheduleSnapshot snapshot = engine.save(value);
      runtimeState.applySnapshot(value, snapshot);
      save(value);
      return query.view(value);
    } catch (RuntimeException exception) {
      value.setStatus("OFFLINE");
      try {
        engine.pauseIfPresent(value.getId());
      } catch (RuntimeException ignored) {
        // 保留原始注册异常，启动 reconcile 会再次清理残留计划。
      }
      throw exception;
    }
  }

  public WorkflowScheduleVO offline(String id) {
    WorkflowSchedulePO value = query.require(id);
    engine.pauseIfPresent(value.getId());
    value.setStatus("OFFLINE");
    value.setNextFireTime(null);
    value.setUpdateTime(Instant.now());
    save(value);
    return query.view(value);
  }

  /** 生效区间结束后由 Schedule Handler 自动停用。 */
  WorkflowScheduleVO expire(String id, Instant fireTime) {
    WorkflowSchedulePO value = query.require(id);
    engine.pauseIfPresent(value.getId());
    value.setStatus("OFFLINE");
    value.setLastFireTime(fireTime);
    value.setNextFireTime(null);
    value.setUpdateTime(Instant.now());
    save(value);
    return query.view(value);
  }

  public void remove(String id) {
    WorkflowSchedulePO value = query.require(id);
    if ("ONLINE".equals(value.getStatus())) {
      throw new IllegalStateException("已启用的调度请先下线后再删除");
    }
    engine.deleteIfPresent(value.getId());
    if (dao.deleteSchedule(value.getId()) != 1) {
      throw new IllegalStateException("删除工作流调度失败：" + value.getId());
    }
  }

  private void save(WorkflowSchedulePO value) {
    if (dao.updateSchedule(value) != 1) throw new IllegalStateException("保存工作流调度失败");
  }
}
