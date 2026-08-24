package io.yak.ops.business.quality.workspace;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityExecutionWorkspaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to execution workspace projections. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionWorkspaceReader {
  private final QualityExecutionWorkspaceRepository repository;

  public QualityExecutionWorkspaceReader(QualityExecutionWorkspaceRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Execution> page(QualityQuery.ExecutionWorkspace query) {
    return repository.page(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<RuleExecutionWorkspaceItem> pageRules(QualityQuery.ExecutionWorkspace query) {
    return repository.pageRules(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Execution require(String executionNo) {
    return repository.find(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }
}
