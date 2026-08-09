package io.yak.ops.business.quality.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.dao.mapper.QualityExecutionMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleExecutionMapper;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityExecutionDaoImpl implements QualityExecutionDao {
  private final QualityExecutionMapper executionMapper;
  private final QualityRuleExecutionMapper ruleExecutionMapper;
  private final QualityQueryMapper queryMapper;

  @Override
  public boolean hasActive(long monitorId) {
    return executionMapper.selectCount(
        Wrappers.<QualityExecutionPO>lambdaQuery()
            .eq(QualityExecutionPO::getMonitorId, monitorId)
            .in(QualityExecutionPO::getExecutionStatus, List.of("WAITING", "RUNNING"))) > 0;
  }

  @Override
  public long insertExecution(QualityExecutionPO execution) {
    executionMapper.insert(execution);
    if (execution.getId() == null) throw new IllegalStateException("质量检查已创建，但未返回执行编号");
    return execution.getId();
  }

  @Override
  public boolean markRunning(long id, LocalDateTime startedAt) {
    return executionMapper.update(
        null,
        Wrappers.<QualityExecutionPO>lambdaUpdate()
            .eq(QualityExecutionPO::getId, id)
            .eq(QualityExecutionPO::getExecutionStatus, "WAITING")
            .set(QualityExecutionPO::getExecutionStatus, "RUNNING")
            .set(QualityExecutionPO::getCheckResult, "RUNNING")
            .set(QualityExecutionPO::getStartedAt, startedAt)
            .set(QualityExecutionPO::getErrorMessage, null)) > 0;
  }

  @Override public void insertRuleExecution(QualityRuleExecutionPO ruleExecution) { ruleExecutionMapper.insert(ruleExecution); }

  @Override
  public boolean complete(long id, String result, int passed, int failed, int errors, LocalDateTime finishedAt, long durationMs) {
    return executionMapper.update(
        null,
        Wrappers.<QualityExecutionPO>lambdaUpdate()
            .eq(QualityExecutionPO::getId, id)
            .eq(QualityExecutionPO::getExecutionStatus, "RUNNING")
            .set(QualityExecutionPO::getExecutionStatus, "SUCCESS")
            .set(QualityExecutionPO::getCheckResult, result)
            .set(QualityExecutionPO::getPassedRules, passed)
            .set(QualityExecutionPO::getFailedRules, failed)
            .set(QualityExecutionPO::getErrorRules, errors)
            .set(QualityExecutionPO::getFinishedAt, finishedAt)
            .set(QualityExecutionPO::getDurationMs, durationMs)
            .set(QualityExecutionPO::getErrorMessage, null)) > 0;
  }

  @Override
  public boolean fail(long id, String errorMessage, LocalDateTime finishedAt, long durationMs) {
    return executionMapper.update(
        null,
        Wrappers.<QualityExecutionPO>lambdaUpdate()
            .eq(QualityExecutionPO::getId, id)
            .in(QualityExecutionPO::getExecutionStatus, List.of("WAITING", "RUNNING"))
            .set(QualityExecutionPO::getExecutionStatus, "FAILED")
            .set(QualityExecutionPO::getCheckResult, "ERROR")
            .set(QualityExecutionPO::getErrorMessage, errorMessage)
            .set(QualityExecutionPO::getFinishedAt, finishedAt)
            .set(QualityExecutionPO::getDurationMs, durationMs)) > 0;
  }

  @Override public long countExecutions(Map<String, Object> params) { return queryMapper.countExecutions(params); }
  @Override public List<QualityExecutionPO> selectExecutions(Map<String, Object> params) { return queryMapper.selectExecutions(params); }

  @Override
  public QualityExecutionPO selectByExecutionNo(String executionNo) {
    return executionMapper.selectOne(
        Wrappers.<QualityExecutionPO>lambdaQuery()
            .eq(QualityExecutionPO::getExecutionNo, executionNo));
  }

  @Override
  public List<QualityRuleExecutionPO> selectRuleExecutions(long executionId) {
    return ruleExecutionMapper.selectList(
        Wrappers.<QualityRuleExecutionPO>lambdaQuery()
            .eq(QualityRuleExecutionPO::getExecutionId, executionId)
            .orderByAsc(QualityRuleExecutionPO::getId));
  }

  @Override public long countExecutionWorkspace(Map<String, Object> params) { return queryMapper.countExecutionWorkspace(params); }
  @Override public List<QualityExecutionPO> selectExecutionWorkspace(Map<String, Object> params) { return queryMapper.selectExecutionWorkspace(params); }
  @Override public long countRuleExecutionWorkspace(Map<String, Object> params) { return queryMapper.countRuleExecutionWorkspace(params); }
  @Override public List<RuleExecutionWorkspaceRow> selectRuleExecutionWorkspace(Map<String, Object> params) { return queryMapper.selectRuleExecutionWorkspace(params); }
}
