package io.yak.ops.boot.home;

import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.domain.DashboardOverview;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.digitalscreen.application.DigitalScreenApplicationService;
import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 首页系统驾驶舱：只组合各领域稳定的 Reader/Service，不进入 DAO/Mapper。 */
@Service
public class HomeCockpitService {

  private static final Logger LOG = LoggerFactory.getLogger(HomeCockpitService.class);
  private static final int RANGE_DAYS = 7;

  private final ObjectProvider<DataSourceReader> dataSourceReaderProvider;
  private final ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider;
  private final ObjectProvider<DevelopmentNodeService> developmentNodeServiceProvider;
  private final ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider;
  private final ObjectProvider<QualityOverviewReader> qualityReaderProvider;
  private final ObjectProvider<QualityExecutionOverviewReader> qualityExecutionReaderProvider;
  private final HomeAssetOverviewService assetOverviewService;
  private final ObjectProvider<DataServiceReader> dataServiceReaderProvider;
  private final ObjectProvider<DashboardService> dashboardServiceProvider;
  private final ObjectProvider<DigitalScreenApplicationService> digitalScreenServiceProvider;

  public HomeCockpitService(
      ObjectProvider<DataSourceReader> dataSourceReaderProvider,
      ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider,
      ObjectProvider<DevelopmentNodeService> developmentNodeServiceProvider,
      ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider,
      ObjectProvider<QualityOverviewReader> qualityReaderProvider,
      ObjectProvider<QualityExecutionOverviewReader> qualityExecutionReaderProvider,
      HomeAssetOverviewService assetOverviewService,
      ObjectProvider<DataServiceReader> dataServiceReaderProvider,
      ObjectProvider<DashboardService> dashboardServiceProvider,
      ObjectProvider<DigitalScreenApplicationService> digitalScreenServiceProvider) {
    this.dataSourceReaderProvider = dataSourceReaderProvider;
    this.offlineReaderProvider = offlineReaderProvider;
    this.developmentNodeServiceProvider = developmentNodeServiceProvider;
    this.workflowReaderProvider = workflowReaderProvider;
    this.qualityReaderProvider = qualityReaderProvider;
    this.qualityExecutionReaderProvider = qualityExecutionReaderProvider;
    this.assetOverviewService = assetOverviewService;
    this.dataServiceReaderProvider = dataServiceReaderProvider;
    this.dashboardServiceProvider = dashboardServiceProvider;
    this.digitalScreenServiceProvider = digitalScreenServiceProvider;
  }

  public CockpitResponse cockpit() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    LocalDateTime start = end.minusDays(RANGE_DAYS);

    DataSourceSnapshot dataSources = dataSources();
    RuntimeSnapshot integration = offline(start, end);
    CountSnapshot development = development();
    RuntimeSnapshot workflow = workflow(start, end);
    QualitySnapshot quality = quality();
    RuntimeSnapshot qualityRuntime = qualityRuntime(start, end);
    AssetSnapshot assets = assets();
    DataServiceSnapshot services = dataServices();
    ConsumptionSnapshot consumption = consumption();

    List<LifecycleStage> lifecycle = List.of(
        stage(
            "data-source", "数据源", "连接与管理数据源", dataSources.available(),
            dataSources.total(), "数据源", dataSources.issueCount()),
        stage(
            "integration", "数据集成", "离线与实时数据接入", integration.available(),
            integration.executionCount(), "近7日离线执行", integration.failedCount()),
        stage(
            "development", "数据开发", "任务开发与发布", development.available(),
            development.count(), "开发节点", 0L),
        stage(
            "workflow", "工作流调度", "编排任务与周期调度", workflow.available(),
            workflow.executionCount(), "近7日执行", workflow.failedCount()),
        stage(
            "quality", "数据质量", "规则监控与问题发现", quality.available(),
            quality.monitoredTableCount(), "监控表",
            quality.issueCount() + qualityRuntime.failedCount()),
        stage(
            "asset", "数据资产", "数据集与血缘资产", assets.available(),
            assets.datasetCount(), "数据集", 0L),
        stage(
            "service", "数据服务", "将数据发布为 API", services.available(),
            services.total(), "API", 0L),
        stage(
            "consumption", "数据消费", "仪表盘、分析与数字大屏", consumption.available(),
            consumption.total(), "可视化资产", 0L));

    List<AttentionItem> attention = new ArrayList<>();
    if (dataSources.available() && dataSources.issueCount() > 0) {
      attention.add(new AttentionItem(
          "data-source-connection", "WARNING", "数据源连接需要关注",
          dataSources.disconnected() + " 个断开，" + dataSources.unknown() + " 个状态未知",
          dataSources.issueCount()));
    }
    if (integration.failedCount() > 0) {
      attention.add(new AttentionItem(
          "offline-failures", "CRITICAL", "离线同步存在失败",
          "近 7 日有 " + integration.failedCount() + " 次离线同步执行失败",
          integration.failedCount()));
    }
    if (workflow.failedCount() > 0) {
      attention.add(new AttentionItem(
          "workflow-failures", "CRITICAL", "工作流存在失败",
          "近 7 日有 " + workflow.failedCount() + " 次工作流执行失败",
          workflow.failedCount()));
    }
    if (qualityRuntime.failedCount() > 0) {
      attention.add(new AttentionItem(
          "quality-execution-failures", "CRITICAL", "质量执行存在异常",
          "近 7 日有 " + qualityRuntime.failedCount() + " 次质量任务技术执行失败",
          qualityRuntime.failedCount()));
    }
    if (quality.available() && quality.issueCount() > 0) {
      attention.add(new AttentionItem(
          "quality-issues", "WARNING", "数据质量发现问题",
          "近 7 日有 " + quality.issueCount() + " 个问题规则需要确认",
          quality.issueCount()));
    }
    attention.sort(Comparator.comparingInt(item -> severityOrder(item.severity())));

    long attentionTotal = attention.stream().mapToLong(AttentionItem::count).sum();
    HeaderStats header = new HeaderStats(
        dataSources.available() ? dataSources.total() : 0L,
        integration.runningCount() + workflow.runningCount() + qualityRuntime.runningCount(),
        attentionTotal);

    return new CockpitResponse(
        header,
        lifecycle,
        new AttentionSummary(attentionTotal, List.copyOf(attention)));
  }

  private DataSourceSnapshot dataSources() {
    DataSourceReader reader = dataSourceReaderProvider.getIfAvailable();
    if (reader == null) return DataSourceSnapshot.unavailable();
    try {
      DataSourceSummary summary = reader.summary();
      return new DataSourceSnapshot(
          true, summary.total(), summary.disconnected(), summary.unknown());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱数据源摘要失败", exception);
      return DataSourceSnapshot.unavailable();
    }
  }

  private RuntimeSnapshot offline(LocalDateTime start, LocalDateTime end) {
    OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
    if (reader == null) return RuntimeSnapshot.unavailable();
    try {
      OfflineExecutionOverviewReader.Metrics metrics = reader.metrics(start, end);
      return RuntimeSnapshot.of(metrics.successCount(), metrics.runningCount(), metrics.failedCount());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱离线同步摘要失败", exception);
      return RuntimeSnapshot.unavailable();
    }
  }

  private CountSnapshot development() {
    DevelopmentNodeService service = developmentNodeServiceProvider.getIfAvailable();
    if (service == null) return CountSnapshot.unavailable();
    try {
      return new CountSnapshot(true, service.count());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱数据开发摘要失败", exception);
      return CountSnapshot.unavailable();
    }
  }

  private RuntimeSnapshot workflow(LocalDateTime start, LocalDateTime end) {
    WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
    if (reader == null) return RuntimeSnapshot.unavailable();
    try {
      WorkflowExecutionOverviewReader.Metrics metrics = reader.metrics(start, end);
      return RuntimeSnapshot.of(metrics.successCount(), metrics.runningCount(), metrics.failedCount());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱工作流摘要失败", exception);
      return RuntimeSnapshot.unavailable();
    }
  }

  private QualitySnapshot quality() {
    QualityOverviewReader reader = qualityReaderProvider.getIfAvailable();
    if (reader == null) return QualitySnapshot.unavailable();
    try {
      QualityOverviewReader.Overview overview = reader.overview();
      return new QualitySnapshot(true, overview.monitoredTableCount(), overview.recentIssueCount());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱质量摘要失败", exception);
      return QualitySnapshot.unavailable();
    }
  }

  private RuntimeSnapshot qualityRuntime(LocalDateTime start, LocalDateTime end) {
    QualityExecutionOverviewReader reader = qualityExecutionReaderProvider.getIfAvailable();
    if (reader == null) return RuntimeSnapshot.unavailable();
    try {
      QualityExecutionOverviewReader.Metrics metrics = reader.metrics(start, end);
      return RuntimeSnapshot.of(metrics.successCount(), metrics.runningCount(), metrics.failedCount());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱质量运行摘要失败", exception);
      return RuntimeSnapshot.unavailable();
    }
  }

  private AssetSnapshot assets() {
    try {
      HomeAssetOverviewService.OverviewResponse overview = assetOverviewService.overview();
      Long datasets = overview.dataset().datasetCount();
      Long lineageAssets = overview.lineage().assetCount();
      boolean available = datasets != null || lineageAssets != null;
      return new AssetSnapshot(available, datasets == null ? 0L : datasets);
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱资产摘要失败", exception);
      return AssetSnapshot.unavailable();
    }
  }

  private DataServiceSnapshot dataServices() {
    DataServiceReader reader = dataServiceReaderProvider.getIfAvailable();
    if (reader == null) return DataServiceSnapshot.unavailable();
    try {
      return new DataServiceSnapshot(true, reader.count());
    } catch (RuntimeException exception) {
      LOG.warn("加载首页驾驶舱数据服务摘要失败", exception);
      return DataServiceSnapshot.unavailable();
    }
  }

  private ConsumptionSnapshot consumption() {
    long total = 0L;
    boolean available = false;

    DashboardService dashboards = dashboardServiceProvider.getIfAvailable();
    if (dashboards != null) {
      try {
        DashboardOverview overview = dashboards.overview(1);
        total += overview.dashboardCount();
        available = true;
      } catch (RuntimeException exception) {
        LOG.warn("加载首页驾驶舱仪表盘摘要失败", exception);
      }
    }

    DigitalScreenApplicationService screens = digitalScreenServiceProvider.getIfAvailable();
    if (screens != null) {
      try {
        total += screens.count();
        available = true;
      } catch (RuntimeException exception) {
        LOG.warn("加载首页驾驶舱数字大屏摘要失败", exception);
      }
    }
    return new ConsumptionSnapshot(available, total);
  }

  private LifecycleStage stage(
      String key,
      String title,
      String description,
      boolean available,
      long value,
      String valueLabel,
      long issueCount) {
    String status;
    if (!available) status = "UNAVAILABLE";
    else if (issueCount > 0) status = "ATTENTION";
    else if (value > 0) status = "READY";
    else status = "EMPTY";
    return new LifecycleStage(
        key, title, description, status, available ? value : null, valueLabel, issueCount);
  }

  private static int severityOrder(String severity) {
    return switch (severity) {
      case "CRITICAL" -> 0;
      case "WARNING" -> 1;
      default -> 2;
    };
  }

  public record CockpitResponse(
      HeaderStats header,
      List<LifecycleStage> lifecycle,
      AttentionSummary attention) {}

  public record HeaderStats(long dataSourceCount, long runningCount, long attentionCount) {}

  public record LifecycleStage(
      String key,
      String title,
      String description,
      String status,
      Long value,
      String valueLabel,
      long issueCount) {}

  public record AttentionSummary(long total, List<AttentionItem> items) {}

  public record AttentionItem(
      String key,
      String severity,
      String title,
      String description,
      long count) {}

  private record DataSourceSnapshot(
      boolean available,
      long total,
      long disconnected,
      long unknown) {
    long issueCount() { return disconnected + unknown; }
    static DataSourceSnapshot unavailable() { return new DataSourceSnapshot(false, 0, 0, 0); }
  }

  private record RuntimeSnapshot(
      boolean available,
      long executionCount,
      long runningCount,
      long failedCount) {
    static RuntimeSnapshot of(long success, long running, long failed) {
      return new RuntimeSnapshot(true, success + running + failed, running, failed);
    }
    static RuntimeSnapshot unavailable() { return new RuntimeSnapshot(false, 0, 0, 0); }
  }

  private record CountSnapshot(boolean available, long count) {
    static CountSnapshot unavailable() { return new CountSnapshot(false, 0); }
  }

  private record QualitySnapshot(boolean available, long monitoredTableCount, long issueCount) {
    static QualitySnapshot unavailable() { return new QualitySnapshot(false, 0, 0); }
  }

  private record AssetSnapshot(boolean available, long datasetCount) {
    static AssetSnapshot unavailable() { return new AssetSnapshot(false, 0); }
  }

  private record DataServiceSnapshot(boolean available, long total) {
    static DataServiceSnapshot unavailable() { return new DataServiceSnapshot(false, 0); }
  }

  private record ConsumptionSnapshot(boolean available, long total) {}
}
