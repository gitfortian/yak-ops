package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 工作流调度定义启停生命周期；本阶段不会注册真实定时任务。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleLifecycle {
  private final WorkflowDefinitionService definitions;
  private final WorkflowScheduleQuery query;
  private final WorkflowScheduleDao dao;

  public WorkflowScheduleLifecycle(
      WorkflowDefinitionService definitions,
      WorkflowScheduleQuery query,
      WorkflowScheduleDao dao) {
    this.definitions = definitions;
    this.query = query;
    this.dao = dao;
  }

  public WorkflowScheduleVO online(String id) {
    WorkflowSchedulePO value = query.require(id);
    var workflow = definitions.get(value.getWorkflowId());
    if (!"ONLINE".equals(workflow.status()) || workflow.activeVersionId() == null) {
      throw new IllegalStateException("工作流需要先发布并上线，才能启用调度");
    }
    value.setStatus("ONLINE");
    value.setUpdateTime(Instant.now());
    save(value);
    return query.view(value);
  }

  public WorkflowScheduleVO offline(String id) {
    WorkflowSchedulePO value = query.require(id);
    value.setStatus("OFFLINE");
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
    dao.deleteSchedule(value.getId());
  }

  private void save(WorkflowSchedulePO value) {
    if (dao.updateSchedule(value) != 1) throw new IllegalStateException("保存工作流调度失败");
  }
}
