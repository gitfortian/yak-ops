package io.yak.ops.business.quality.service;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.repository.QualityWorkspaceRepository;
import io.yak.ops.business.quality.service.support.QualityViewMapper;
import io.yak.ops.common.bean.vo.quality.QualityWorkspaceVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnQualityEnabled
@Service
public class QualityWorkspaceService {
  private final QualityMonitorService monitorService;
  private final QualityWorkspaceRepository repository;

  public QualityWorkspaceService(QualityMonitorService monitorService, QualityWorkspaceRepository repository) {
    this.monitorService = monitorService;
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityWorkspaceVO.MonitorWorkspace workspace(long monitorId) {
    var stats = repository.stats(monitorId);
    return new QualityWorkspaceVO.MonitorWorkspace(
        monitorService.get(monitorId),
        monitorService.getSettings(monitorId),
        new QualityWorkspaceVO.Stats(stats.ruleCount(), stats.enabledRuleCount(), stats.executionCount(),
            stats.issueExecutionCount(), stats.latestExecutionTime()));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityWorkspaceVO.MonitorReport report(long monitorId, LocalDate reportDate) {
    monitorService.get(monitorId);
    LocalDate normalized = reportDate == null ? LocalDate.now().minusDays(1) : reportDate;
    LocalDate trendStart = normalized.minusDays(6);
    LocalDateTime start = normalized.atStartOfDay();
    LocalDateTime end = normalized.plusDays(1).atStartOfDay();
    var overview = repository.overview(monitorId, start, end);
    return new QualityWorkspaceVO.MonitorReport(
        normalized,
        trendStart,
        new QualityWorkspaceVO.ReportOverview(overview.totalRules(), overview.enabledRules(), overview.executedRules(),
            overview.issueRules(), overview.errorRules(), overview.passRate()),
        repository.dimensions(monitorId, start, end).stream()
            .map(v -> new QualityWorkspaceVO.DimensionReport(v.dimension(), v.total(), v.passed(), v.notPassed(), v.errors(), v.passRate()))
            .toList(),
        repository.trend(monitorId, trendStart.atStartOfDay(), end).stream()
            .map(v -> new QualityWorkspaceVO.TrendPoint(v.date(), v.dimension(), v.total(), v.passed(), v.issues(), v.passRate()))
            .toList(),
        repository.columns(monitorId, start, end).stream()
            .map(v -> new QualityWorkspaceVO.ColumnReport(v.columnName(), v.dimension(), v.total(), v.passed(), v.issues(), v.passRate()))
            .toList());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityWorkspaceVO.OperationLogPage operationLogs(long monitorId, Integer current, Integer pageSize) {
    monitorService.get(monitorId);
    int page = current == null || current < 1 ? 1 : current;
    int size = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 100);
    return new QualityWorkspaceVO.OperationLogPage(
        repository.operationLogs(monitorId, page, size).stream().map(QualityViewMapper::operationLog).toList(),
        repository.countOperationLogs(monitorId), page, size);
  }
}
