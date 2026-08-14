package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowBackfillMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的 Backfill 批次 DAO。 */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillDaoImpl implements WorkflowBackfillDao {
  private final WorkflowBackfillMapper mapper;

  @Override
  public int insert(WorkflowBackfillPO backfill) {
    return mapper.insert(backfill);
  }

  @Override
  public int update(WorkflowBackfillPO backfill) {
    return mapper.updateById(backfill);
  }

  @Override
  public WorkflowBackfillPO select(String id) {
    return mapper.selectById(id);
  }

  @Override
  public List<WorkflowBackfillPO> selectList(String workflowId, String scheduleId) {
    var query = Wrappers.<WorkflowBackfillPO>lambdaQuery();
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowBackfillPO::getWorkflowId, workflowId.trim());
    }
    if (scheduleId != null && !scheduleId.isBlank()) {
      query.eq(WorkflowBackfillPO::getScheduleId, scheduleId.trim());
    }
    return mapper.selectList(query.orderByDesc(WorkflowBackfillPO::getCreateTime));
  }
}
