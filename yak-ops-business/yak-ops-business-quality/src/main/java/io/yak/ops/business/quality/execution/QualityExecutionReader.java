package io.yak.ops.business.quality.execution;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to execution evidence. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionReader {
  private final QualityExecutionRepository repository;

  public QualityExecutionReader(QualityExecutionRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Execution> page(QualityQuery.Execution query) {
    return repository.pageExecutions(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Execution require(String executionNo) {
    return repository.findExecution(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }
}
