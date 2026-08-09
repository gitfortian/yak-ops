package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeAttemptMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeExecutionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流执行 DAO。 */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionDaoImpl implements WorkflowExecutionDao {
  private final WorkflowExecutionMapper executionMapper;
  private final WorkflowNodeExecutionMapper nodeExecutionMapper;
  private final WorkflowNodeAttemptMapper nodeAttemptMapper;

  @Override
  public WorkflowExecutionPO selectExecution(String executionId) {
    return executionMapper.selectById(executionId);
  }

  @Override
  public List<WorkflowNodeExecutionPO> selectNodeExecutions(String executionId) {
    return nodeExecutionMapper.selectList(
        Wrappers.<WorkflowNodeExecutionPO>lambdaQuery()
            .eq(WorkflowNodeExecutionPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeExecutionPO::getId));
  }

  @Override
  public List<WorkflowNodeAttemptPO> selectNodeAttempts(String executionId) {
    return nodeAttemptMapper.selectList(
        Wrappers.<WorkflowNodeAttemptPO>lambdaQuery()
            .eq(WorkflowNodeAttemptPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeAttemptPO::getNodeExecutionId)
            .orderByAsc(WorkflowNodeAttemptPO::getAttemptNo));
  }

  @Override
  public int upsertExecution(WorkflowExecutionPO execution) {
    return executionMapper.upsert(execution);
  }

  @Override
  public int upsertNodeExecution(WorkflowNodeExecutionPO nodeExecution) {
    return nodeExecutionMapper.upsert(nodeExecution);
  }

  @Override
  public int upsertNodeAttempt(WorkflowNodeAttemptPO attempt) {
    return nodeAttemptMapper.upsert(attempt);
  }

  @Override
  public int updateExecution(WorkflowExecutionPO execution) {
    return executionMapper.updateById(execution);
  }

  @Override
  public List<String> selectExecutionIds() {
    return executionMapper.selectExecutionIds();
  }

  @Override
  public List<String> selectRecoverableExecutionIds() {
    return executionMapper.selectRecoverableExecutionIds();
  }

  @Override
  public String selectEffectiveRuntimeMetadata(String executionId) {
    return executionMapper.selectEffectiveRuntimeMetadata(executionId);
  }

  @Override
  public WorkflowNodeAttemptPO selectAttempt(String attemptId) {
    return nodeAttemptMapper.selectById(attemptId);
  }

  @Override
  public int bindExternalExecution(String attemptId, String externalExecutionId) {
    return nodeAttemptMapper.bindExternalExecution(attemptId, externalExecutionId);
  }
}
