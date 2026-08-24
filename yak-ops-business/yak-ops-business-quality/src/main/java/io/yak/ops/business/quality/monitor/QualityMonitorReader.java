package io.yak.ops.business.quality.monitor;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to quality monitor definitions and settings. */
@Component
@ConditionalOnQualityEnabled
public class QualityMonitorReader {
  private final QualityMonitorRepository repository;

  public QualityMonitorReader(QualityMonitorRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Monitor> page(QualityQuery.Monitor query) {
    return repository.pageMonitors(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Monitor require(long id) {
    return repository.findMonitor(id)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + id));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public MonitorSettings settings(long id) {
    require(id);
    return repository.findMonitorSettings(id);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<TableMonitorSummary> tableSummaries(
      long dataSourceId, String databaseName, String schemaName) {
    if (dataSourceId <= 0L) throw new IllegalArgumentException("数据源编号无效");
    return repository.tableSummaries(dataSourceId, databaseName, schemaName);
  }
}
