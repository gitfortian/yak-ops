package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.util.List;

/** 工作流调度定义数据访问接口。 */
public interface WorkflowScheduleDao {
  List<WorkflowSchedulePO> selectSchedules(String workflowId, String status);

  WorkflowSchedulePO selectSchedule(String id);

  int insertSchedule(WorkflowSchedulePO schedule);

  int updateSchedule(WorkflowSchedulePO schedule);

  int updateRuntimeState(String id, Instant lastFireTime, Instant nextFireTime);

  int deleteSchedule(String id);
}
