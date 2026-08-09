package io.yak.ops.business.sync.offline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.OfflinePage;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.service.support.OfflineSyncViewMapper;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobExecutionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionEventVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionDetailVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 执行实例、指标和状态事件读模型。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionReadService {
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineExecutionEventRepository eventRepository;
  private final LinkUpClient linkUpClient;
  private final ObjectMapper objectMapper;
  private final OfflineSyncViewMapper viewMapper;

  public OfflineExecutionReadService(
      OfflineJobExecutionRepository executionRepository,
      OfflineExecutionEventRepository eventRepository,
      LinkUpClient linkUpClient,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      OfflineSyncViewMapper viewMapper) {
    this.executionRepository = executionRepository;
    this.eventRepository = eventRepository;
    this.linkUpClient = linkUpClient;
    this.objectMapper = objectMapper;
    this.viewMapper = viewMapper;
  }

  public OfflineJobExecution require(Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("任务实例 ID 不合法");
    return executionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务实例不存在：" + id));
  }

  public PagingData<OfflineJobExecutionVO> page(OfflineJobExecutionQueryDTO queryDTO) {
    OfflineJobExecutionQueryDTO query = queryDTO == null ? new OfflineJobExecutionQueryDTO() : queryDTO;
    OfflinePage<OfflineJobExecution> page = executionRepository.page(
        new OfflineExecutionQuery(
            query.getCurrent(), query.getPageSize(), query.getJobDefinitionId(), query.getStatus()));
    List<OfflineJobExecutionVO> records = page.records().stream().map(viewMapper::execution).toList();
    return new PagingData<>(
        records,
        PagingData.Pagination.builder()
            .total(page.total())
            .pages(page.pages())
            .pageNo(page.current())
            .pageSize(page.pageSize())
            .build());
  }

  public OfflineJobExecutionDetailVO detail(Long id) {
    OfflineJobExecution execution = require(id);
    OfflineJobExecutionVO summary = viewMapper.execution(execution);
    OfflineJobExecutionDetailVO detail = OfflineJobExecutionDetailVO.builder()
        .execution(summary)
        .summary(summary)
        .build();

    if (StringUtils.hasText(execution.getEngineSnapshotJson())) {
      try {
        JsonNode snapshot = objectMapper.readTree(execution.getEngineSnapshotJson());
        detail.setJob(snapshot);
        detail.setPipelines(snapshot.path("pipelines"));
        detail.setTasks(snapshot.path("tasks"));
        detail.setMetrics(snapshot.path("metrics"));
      } catch (Exception ignored) {
        // 历史快照损坏不影响执行实例基本信息查询。
      }
    }
    return detail;
  }

  public JsonNode tableMetrics(Long id) {
    OfflineJobExecution execution = require(id);
    if (!OfflineExecutionStatus.isActive(execution.getStatus())) {
      JsonNode snapshotPipelines = snapshotPipelines(execution);
      if (snapshotPipelines.isArray() && !snapshotPipelines.isEmpty()) {
        return OfflinePipelineMetricsMapper.flatten(objectMapper, snapshotPipelines);
      }
    }
    if (!StringUtils.hasText(execution.getEngineJobId())) {
      throw new IllegalStateException("当前执行实例尚未获得 Link-Up jobId");
    }
    return OfflinePipelineMetricsMapper.flatten(
        objectMapper,
        linkUpClient.pipelines(execution.getEngineJobId()));
  }

  public List<OfflineExecutionEventVO> events(Long executionId) {
    return eventRepository.list(executionId).stream().map(viewMapper::event).toList();
  }

  /** 保留旧文本状态事件视图；统一物理日志由 OfflineExecutionLogService 提供。 */
  public String logs(Long id) {
    OfflineJobExecution execution = require(id);
    StringBuilder log = new StringBuilder("# Yak Ops Offline Sync\n");
    log.append("definitionId: ").append(execution.getJobDefinitionId()).append('\n')
        .append("executionId: ").append(execution.getId()).append('\n')
        .append("definitionVersion: ").append(value(execution.getDefinitionVersion())).append('\n')
        .append("externalExecutionId: ").append(text(execution.getExternalExecutionId())).append('\n')
        .append("engineBaseUrl: ").append(text(execution.getEngineBaseUrl())).append('\n')
        .append("workerInstanceId: ").append(text(execution.getWorkerInstanceId())).append('\n')
        .append("engineJobId: ").append(text(execution.getEngineJobId())).append('\n')
        .append("status: ").append(text(execution.getStatus())).append('\n')
        .append("attemptNo: ").append(value(execution.getAttemptNo())).append('\n')
        .append("sourceRecordCount: ").append(value(execution.getSourceRecordCount())).append('\n')
        .append("sinkAttemptedRecordCount: ").append(value(execution.getSinkAttemptedRecordCount())).append('\n')
        .append("sinkSuccessRecordCount: ").append(value(execution.getSinkSuccessRecordCount())).append('\n')
        .append("sinkCommittedRecordCount: ").append(value(execution.getSinkCommittedRecordCount())).append('\n')
        .append("sourceAverageQps: ").append(decimal(execution.getSourceAverageQps())).append('\n')
        .append("sinkAverageQps: ").append(decimal(execution.getSinkAverageQps())).append('\n')
        .append("durationMillis: ").append(value(execution.getDurationMillis())).append('\n');
    if (StringUtils.hasText(execution.getErrorMessage())) {
      log.append("error: ").append(execution.getErrorMessage()).append('\n');
    }
    log.append("\n# State Events\n");
    for (OfflineExecutionEvent event : eventRepository.list(id)) {
      log.append(format(event.getCreateTime()))
          .append(" [").append(event.getEventType()).append("] ")
          .append(text(event.getFromStatus())).append(" -> ").append(text(event.getToStatus()));
      if (StringUtils.hasText(event.getMessage())) log.append(" | ").append(event.getMessage());
      log.append('\n');
    }
    return log.toString();
  }

  public OfflineJobExecutionVO toVO(OfflineJobExecution execution) {
    return viewMapper.execution(execution);
  }

  private JsonNode snapshotPipelines(OfflineJobExecution execution) {
    if (execution == null || !StringUtils.hasText(execution.getEngineSnapshotJson())) {
      return objectMapper.createArrayNode();
    }
    try {
      JsonNode snapshot = objectMapper.readTree(execution.getEngineSnapshotJson());
      JsonNode pipelines = snapshot.path("pipelines");
      return pipelines.isArray() ? pipelines : objectMapper.createArrayNode();
    } catch (Exception ignored) {
      return objectMapper.createArrayNode();
    }
  }

  private String format(LocalDateTime value) {
    return value == null ? null : value.format(FORMAT);
  }

  private String text(String value) {
    return StringUtils.hasText(value) ? value : "-";
  }

  private String decimal(Double value) {
    return value == null ? "0" : String.format(java.util.Locale.ROOT, "%.3f", value);
  }

  private long value(Long value) { return value == null ? 0L : value; }
  private int value(Integer value) { return value == null ? 0 : value; }
}
