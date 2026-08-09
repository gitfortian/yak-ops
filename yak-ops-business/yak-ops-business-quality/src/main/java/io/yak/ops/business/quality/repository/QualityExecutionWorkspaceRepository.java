package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.Page;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import java.util.Optional;

/** 执行工作台读模型 Repository。 */
public interface QualityExecutionWorkspaceRepository {
  Page<Execution> page(QualityQuery.ExecutionWorkspace query);
  Page<RuleExecutionWorkspaceItem> pageRules(QualityQuery.ExecutionWorkspace query);
  Optional<Execution> find(String executionNo);
}
