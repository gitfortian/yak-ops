package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流调度定义 DAO。 */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleDaoImpl implements WorkflowScheduleDao {
  private final WorkflowScheduleMapper mapper;

  @Override
  public List<WorkflowSchedulePO> selectSchedules(String workflowId, String status) {
    var query = Wrappers.<WorkflowSchedulePO>lambdaQuery();
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowSchedulePO::getWorkflowId, workflowId.trim());
    }
    if (status != null && !status.isBlank()) {
      query.eq(WorkflowSchedulePO::getStatus, status.trim().toUpperCase());
    }
    return mapper.selectList(query.orderByDesc(WorkflowSchedulePO::getUpdateTime));
  }

  @Override
  public WorkflowSchedulePO selectSchedule(String id) {
    return mapper.selectById(id);
  }

  @Override
  public int insertSchedule(WorkflowSchedulePO schedule) {
    return mapper.insert(schedule);
  }

  @Override
  public int updateSchedule(WorkflowSchedulePO schedule) {
    return mapper.updateById(schedule);
  }

  @Override
  public int deleteSchedule(String id) {
    return mapper.deleteById(id);
  }
}
