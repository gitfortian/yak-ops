package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.util.List;

/** 工作流调度 Trigger Ledger 数据访问接口。 */
public interface WorkflowScheduleTriggerDao {
  WorkflowScheduleTriggerPO claim(WorkflowScheduleTriggerPO trigger);
  WorkflowScheduleTriggerPO selectBySchedulePlan(String scheduleId, Instant plannedFireTime);
  WorkflowScheduleTriggerPO selectByExecutionId(String executionId);
  WorkflowScheduleTriggerPO selectNextWaiting(String workflowId);
  List<WorkflowScheduleTriggerPO> selectPending();
  List<WorkflowScheduleTriggerPO> selectQueuedBySchedule(String scheduleId);
  List<WorkflowScheduleTriggerPO> selectTriggers(String scheduleId, String workflowId, String status, int limit);
  int update(WorkflowScheduleTriggerPO trigger);
  void lockWorkflow(String workflowId);
  long countActiveExecutions(String workflowId);
  long countLaunchingTriggers(String workflowId);
  long countWaitingTriggers(String workflowId);
  String selectWorkflowIdByExecution(String executionId);
  String selectExecutionIdByTrigger(String triggerId);
}
