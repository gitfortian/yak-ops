package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleTriggerVO;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Trigger Ledger 查询服务。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleTriggerQuery {
  private static final Set<String> STATUSES = Set.of(
      "RECEIVED", "WAITING", "LAUNCHING", "RUNNING",
      "SUCCEEDED", "FAILED", "CANCELED", "SKIPPED");

  private final WorkflowScheduleTriggerDao dao;

  public WorkflowScheduleTriggerQuery(WorkflowScheduleTriggerDao dao) {
    this.dao = dao;
  }

  public List<WorkflowScheduleTriggerVO> list(
      String scheduleId, String workflowId, String status, Integer limit) {
    String state = normalize(status);
    if (state != null && !STATUSES.contains(state)) {
      throw new IllegalArgumentException("不支持的 Trigger 状态：" + state);
    }
    int safeLimit = limit == null ? 100 : limit;
    return dao.selectTriggers(scheduleId, workflowId, state, safeLimit)
        .stream().map(this::view).toList();
  }

  WorkflowScheduleTriggerVO view(WorkflowScheduleTriggerPO value) {
    return new WorkflowScheduleTriggerVO(
        value.getId(),
        value.getScheduleId(),
        value.getWorkflowId(),
        value.getTriggerId(),
        value.getTriggerSource(),
        value.getPlannedFireTime(),
        value.getActualFireTime(),
        value.getExecutionStrategy(),
        value.getMisfireStrategy(),
        value.getStatus(),
        value.getWorkflowExecutionId(),
        value.getExecutionStatus(),
        value.getMessage(),
        value.getErrorMessage(),
        value.getLaunchedAt(),
        value.getCompletedAt(),
        value.getCreateTime(),
        value.getUpdateTime());
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim().toUpperCase();
  }
}
