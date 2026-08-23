package io.yak.ops.business.sync.offline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpRequestException;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionClaimService.ClaimResult;
import io.yak.ops.business.sync.offline.service.support.OfflineBatchScopeExecutionAdapter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 离线任务提交、取消和状态落库。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionOrchestrator {
  private final OfflineJobDefinitionService definitionService;
  private final OfflineExecutionClaimService claimService;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineBatchRuntimeService batchRuntimeService;
  private final OfflineBatchScopeExecutionAdapter scopeExecutionAdapter;
  private final OfflineExecutionEventRepository eventRepository;
  private final LinkUpClient linkUpClient;
  private final ObjectMapper objectMapper;

  public OfflineExecutionOrchestrator(
      OfflineJobDefinitionService definitionService,
      OfflineExecutionClaimService claimService,
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineBatchRuntimeService batchRuntimeService,
      OfflineBatchScopeExecutionAdapter scopeExecutionAdapter,
      OfflineExecutionEventRepository eventRepository,
      LinkUpClient linkUpClient,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.definitionService = definitionService;
    this.claimService = claimService;
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.batchRuntimeService = batchRuntimeService;
    this.scopeExecutionAdapter = scopeExecutionAdapter;
    this.eventRepository = eventRepository;
    this.linkUpClient = linkUpClient;
    this.objectMapper = objectMapper;
  }

  public OfflineJobExecution execute(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    ClaimResult claim = claimService.claim(definitionId, triggerType, retryFromExecutionId, attemptNo);
    return submitClaim(claim, resolveScopedExecutionJobSpec(claim));
  }

  /** 按工作流版本固定的任务配置快照执行，不回读任务当前 JobSpec。 */
  public OfflineJobExecution executeSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    return executeSnapshot(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        null);
  }

  /** 按工作流版本快照和 Attempt 幂等键执行。 */
  public OfflineJobExecution executeSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String idempotencyKey) {
    ClaimResult claim = claimService.claimSnapshot(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        "WORKFLOW",
        idempotencyKey);
    return submitClaim(claim, resolveScopedExecutionJobSpec(claim));
  }

  /** Wave 5 dispatcher：只提交已经物化并 reservation 的 Backfill Batch。 */
  public OfflineJobExecution executePendingBackfill(Long batchId) {
    ClaimResult claim = claimService.claimPendingBackfill(batchId);
    OfflineJobExecution execution = claim.getExecution();
    if (claim.isReused()
        && !OfflineExecutionStatus.CREATED.name().equalsIgnoreCase(execution.getStatus())) {
      return execution;
    }
    return submitClaim(claim, resolveScopedExecutionJobSpec(claim));
  }

  private String resolveScopedExecutionJobSpec(ClaimResult claim) {
    OfflineJobExecution execution = claim.getExecution();
    String logicalJobSpec = claim.getLogicalJobSpecJson();
    Long batchId = execution.getBatchId();
    if (batchId != null && batchId > 0L) {
      BatchExecution batch = batchRepository.findById(batchId)
          .orElseThrow(() -> new IllegalStateException("Attempt 绑定的 BatchExecution 不存在：" + batchId));
      logicalJobSpec = scopeExecutionAdapter.apply(
          batch.taskId(),
          logicalJobSpec,
          batch.batchScope());
    }
    return definitionService.resolveExecutionJobSpec(logicalJobSpec);
  }

  private OfflineJobExecution submitClaim(ClaimResult claim, String resolvedExecutionJobSpec) {
    OfflineJobExecution execution = claim.getExecution();
    record(
        execution,
        null,
        execution.getStatus(),
        "EXECUTION_CREATED",
        "使用 application.yml 中的固定 Link-Up 地址",
        null);
    try {
      LinkUpNodeResponse node = linkUpClient.node();
      execution.setWorkerInstanceId(node.getInstanceId());
      execution.setUpdateTime(LocalDateTime.now());
      batchRuntimeService.persistAttempt(execution);

      JsonNode jobSpec = readJobSpec(resolvedExecutionJobSpec);
      transition(
          execution,
          OfflineExecutionStatus.SUBMITTED,
          "SUBMITTING",
          "正在向 Link-Up 提交 JobSpec",
          null);
      LinkUpJobResponse response = linkUpClient.submit(
          execution.getExternalExecutionId(),
          execution.getIdempotencyKey(),
          execution.getDefinitionVersion(),
          jobSpec);
      applySnapshot(execution, response, "SUBMITTED");
      return execution;
    } catch (LinkUpRequestException exception) {
      markTerminal(
          execution,
          OfflineExecutionStatus.FAILED,
          exception.getCode() + "：" + exception.getMessage(),
          null,
          exception.getStatusCode() == 429 || exception.getStatusCode() >= 500);
      throw exception;
    } catch (LinkUpTransportException exception) {
      if (exception.isUncertain()) {
        String previous = execution.getStatus();
        execution.setStatus(OfflineExecutionStatus.UNKNOWN.name());
        execution.setErrorMessage(exception.getMessage());
        execution.setNextRetryTime(null);
        execution.setEndTime(null);
        execution.setLastSyncTime(LocalDateTime.now());
        execution.setUpdateTime(LocalDateTime.now());
        batchRuntimeService.persistAttempt(execution);
        projectTaskLastState(execution, OfflineExecutionStatus.UNKNOWN.name());
        record(
            execution,
            previous,
            OfflineExecutionStatus.UNKNOWN.name(),
            "SUBMIT_UNCERTAIN",
            exception.getMessage(),
            null);
        return execution;
      }
      markTerminal(execution, OfflineExecutionStatus.FAILED, exception.getMessage(), null, true);
      throw exception;
    } catch (RuntimeException exception) {
      markTerminal(execution, OfflineExecutionStatus.FAILED, exception.getMessage(), null, false);
      throw exception;
    }
  }

  /** Retry 只在原 Batch 内创建新 Attempt，并使用 Batch 冻结证据。 */
  public OfflineJobExecution retryFrom(OfflineJobExecution previous) {
    if (previous == null || previous.getId() == null) {
      throw new IllegalArgumentException("重试来源实例不能为空");
    }
    ClaimResult claim = claimService.claimRetry(previous.getId());
    OfflineJobExecution execution = claim.getExecution();
    if (claim.isReused()
        && !OfflineExecutionStatus.CREATED.name().equalsIgnoreCase(execution.getStatus())) {
      return execution;
    }
    return submitClaim(claim, resolveScopedExecutionJobSpec(claim));
  }

  /** Task 级停止命令从 Batch runtime truth 选择 latest Attempt，不读取 Task.lastExecutionId。 */
  public OfflineJobExecution cancelLatestBatch(Long definitionId) {
    BatchExecution batch = batchRuntimeService.requireLatestOccupyingBatch(definitionId);
    if (batch.status() == BatchStatus.WAITING_RETRY) {
      OfflineJobExecution latest = batchRuntimeService.cancelWaitingRetry(batch);
      projectTaskLastState(latest, BatchStatus.CANCELED.name());
      record(
          latest,
          latest.getStatus(),
          latest.getStatus(),
          "BATCH_CANCELLED_WAITING_RETRY",
          "已取消 Batch 的 Retry 等待，不再创建新的 Attempt",
          null);
      return latest;
    }
    OfflineJobExecution latest = batchRuntimeService.requireLatestAttempt(batch);
    return cancel(latest.getId());
  }

  public OfflineJobExecution cancel(Long id) {
    OfflineJobExecution execution = require(id);
    ensureLatestAttemptForCancel(execution);
    if (!OfflineExecutionStatus.isActive(execution.getStatus())) {
      throw new IllegalStateException("当前执行实例已结束，无需停止");
    }
    execution.setCancellationRequested(true);
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntimeService.persistAttempt(execution);
    record(
        execution,
        execution.getStatus(),
        execution.getStatus(),
        "CANCEL_REQUESTED",
        "Yak Ops 已记录取消意图",
        null);
    if (StringUtils.hasText(execution.getEngineJobId())) {
      applySnapshot(execution, linkUpClient.cancel(execution.getEngineJobId()), "CANCEL_ACCEPTED");
    }
    return execution;
  }

  public void applySnapshot(
      OfflineJobExecution execution,
      LinkUpJobResponse response,
      String eventType) {
    if (execution == null || response == null) return;

    String previous = execution.getStatus();
    OfflineExecutionStatus next = StringUtils.hasText(response.getStatus())
        ? OfflineExecutionStatus.parse(response.getStatus())
        : OfflineExecutionStatus.parse(execution.getStatus());

    execution.setEngineJobId(first(response.getJobId(), execution.getEngineJobId()));
    execution.setWorkerInstanceId(first(response.getWorkerInstanceId(), execution.getWorkerInstanceId()));
    execution.setStatus(next.name());
    execution.setStateVersion(
        Math.max(value(execution.getStateVersion(), 0L), value(response.getStateVersion(), 0L)));
    execution.setCancellationRequested(
        Boolean.TRUE.equals(response.getCancellationRequested())
            || Boolean.TRUE.equals(execution.getCancellationRequested()));
    execution.setEngineSnapshotJson(write(response));
    execution.setErrorMessage(response.getErrorMessage());
    applyMetrics(execution, response);
    execution.setDurationMillis(value(response.getDurationMillis(), 0L));
    execution.setStartTime(time(response.getStartTimeMillis()));
    execution.setEndTime(time(response.getEndTimeMillis()));
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    configureRetry(execution, next, retryable(response, next));
    batchRuntimeService.persistAttempt(execution);
    projectTaskLastState(execution, next.name());

    if (!next.name().equals(previous)) {
      record(
          execution,
          previous,
          next.name(),
          eventType,
          response.getErrorMessage(),
          execution.getEngineSnapshotJson());
    }
  }

  private void applyMetrics(OfflineJobExecution execution, LinkUpJobResponse response) {
    JsonNode metrics = response.getMetrics();
    JsonNode commitSummary = response.getCommitSummary();

    long sourceRecordCount = number(metrics, "sourceRecordCount", 0L);
    long sinkAttemptedRecordCount = number(metrics, "sinkAttemptedRecordCount", 0L);
    long sinkSuccessRecordCount = number(metrics, "sinkSuccessRecordCount", 0L);
    long sinkCommittedRecordCount = number(
        commitSummary, "successfullyCommittedRecordCount", sinkSuccessRecordCount);
    double sourceAverageQps = decimal(metrics, "sourceAverageQps", 0D);
    double sinkAverageQps = decimal(metrics, "sinkAverageQps", 0D);

    execution.setSourceRecordCount(sourceRecordCount);
    execution.setSinkAttemptedRecordCount(sinkAttemptedRecordCount);
    execution.setSinkSuccessRecordCount(sinkSuccessRecordCount);
    execution.setSinkCommittedRecordCount(sinkCommittedRecordCount);
    execution.setSourceReadBytes(number(metrics, "sourceReadBytes", 0L));
    execution.setSinkWrittenBytes(number(metrics, "sinkWrittenBytes", 0L));
    execution.setSourceAverageQps(sourceAverageQps);
    execution.setSinkAverageQps(sinkAverageQps);
    execution.setFailedRecordCount(number(metrics, "failedRecordCount", 0L));
    execution.setSkippedRecordCount(number(metrics, "skippedRecordCount", 0L));
    execution.setDatabaseCommitMillis(number(metrics, "databaseCommitMillis", 0L));
    execution.setSqlExecutionMillis(number(metrics, "sqlExecutionMillis", 0L));
    execution.setQps(sourceAverageQps > 0D ? sourceAverageQps : sinkAverageQps);
  }

  /** 结果无法确认时进入 UNKNOWN，继续 reconcile，并明确禁止自动 Retry。 */
  public void markUnknown(OfflineJobExecution execution, String message) {
    if (execution == null || !OfflineExecutionStatus.isActive(execution.getStatus())) return;
    if (OfflineExecutionStatus.UNKNOWN.name().equalsIgnoreCase(execution.getStatus())) return;

    String previous = execution.getStatus();
    execution.setStatus(OfflineExecutionStatus.UNKNOWN.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setErrorMessage(message);
    execution.setNextRetryTime(null);
    execution.setEndTime(null);
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntimeService.persistAttempt(execution);
    projectTaskLastState(execution, OfflineExecutionStatus.UNKNOWN.name());
    record(
        execution,
        previous,
        OfflineExecutionStatus.UNKNOWN.name(),
        "UNKNOWN",
        message,
        null);
  }

  public OfflineJobExecution require(Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("任务实例 ID 不合法");
    return executionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务实例不存在：" + id));
  }

  private void markTerminal(
      OfflineJobExecution execution,
      OfflineExecutionStatus status,
      String message,
      String payload,
      boolean retryable) {
    String previous = execution.getStatus();
    execution.setStatus(status.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setErrorMessage(message);
    execution.setEndTime(LocalDateTime.now());
    execution.setLastSyncTime(LocalDateTime.now());
    execution.setUpdateTime(LocalDateTime.now());
    configureRetry(execution, status, retryable);
    batchRuntimeService.persistAttempt(execution);
    projectTaskLastState(execution, status.name());
    record(execution, previous, status.name(), status.name(), message, payload);
  }

  /** Task last-* 只维护 latest Attempt / Batch 的查询投影。 */
  private void projectTaskLastState(OfflineJobExecution execution, String fallbackStatus) {
    String projectedStatus = fallbackStatus;
    Long batchId = execution.getBatchId();
    if (batchId != null && batchId > 0L) {
      List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batchId);
      if (!attempts.isEmpty()) {
        OfflineJobExecution latest = attempts.stream()
            .max(
                Comparator.comparingInt((OfflineJobExecution value) -> value(value.getAttemptNo(), 1))
                    .thenComparingLong(value -> value(value.getId(), 0L)))
            .orElseThrow();
        if (!Objects.equals(latest.getId(), execution.getId())) return;
      }
      BatchExecution batch = batchRepository.findById(batchId).orElse(null);
      if (batch != null) projectedStatus = batch.status().name();
    }

    OfflineJobDefinition definition = definitionRepository.findById(execution.getJobDefinitionId()).orElse(null);
    if (definition == null) return;
    definition.setLastExecutionId(execution.getId());
    definition.setLastEngineJobId(execution.getEngineJobId());
    definition.setLastJobStatus(projectedStatus);
    definition.setLastErrorMessage(execution.getErrorMessage());
    definition.setLastDurationMillis(execution.getDurationMillis());
    definition.setLastReadRowCount(execution.getSourceRecordCount());
    definition.setLastQps(execution.getQps());
    definition.setLastSyncBytes(
        Math.max(value(execution.getSourceReadBytes(), 0L), value(execution.getSinkWrittenBytes(), 0L)));
    definition.setLastStartTime(execution.getStartTime());
    definition.setLastEndTime(execution.getEndTime());
    definition.setUpdateTime(LocalDateTime.now());
    definitionRepository.update(definition);
  }

  private void transition(
      OfflineJobExecution execution,
      OfflineExecutionStatus target,
      String type,
      String message,
      String payload) {
    String previous = execution.getStatus();
    execution.setStatus(target.name());
    execution.setStateVersion(value(execution.getStateVersion(), 0L) + 1L);
    execution.setUpdateTime(LocalDateTime.now());
    batchRuntimeService.persistAttempt(execution);
    record(execution, previous, target.name(), type, message, payload);
  }

  /** 使用 Batch 创建时冻结的 RetryPolicy Snapshot，禁止回读 current SchedulePolicy。 */
  private void configureRetry(
      OfflineJobExecution execution,
      OfflineExecutionStatus status,
      boolean retryable) {
    execution.setNextRetryTime(null);
    if (!retryable || status != OfflineExecutionStatus.FAILED) return;
    RetryPolicySnapshot retryPolicy = frozenRetryPolicy(execution);
    if (retryPolicy == null) return;
    if (value(execution.getAttemptNo(), 1) < retryPolicy.maxAttempts()) {
      execution.setNextRetryTime(
          LocalDateTime.now().plusSeconds(Math.max(0, retryPolicy.backoffSeconds())));
    }
  }

  private RetryPolicySnapshot frozenRetryPolicy(OfflineJobExecution execution) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) return null;
    BatchExecution batch = batchRepository.findById(batchId).orElse(null);
    if (batch == null || !Objects.equals(execution.getJobDefinitionId(), batch.taskId())) return null;
    return batch.snapshot().retryPolicy();
  }

  private void ensureLatestAttemptForCancel(OfflineJobExecution execution) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) return;
    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("Attempt 绑定的 BatchExecution 不存在：" + batchId));
    Long latestId = batch.latestAttempt().map(attempt -> attempt.id()).orElse(null);
    if (!Objects.equals(latestId, execution.getId())) {
      throw new IllegalStateException("只能停止 Batch 的 latest Attempt");
    }
  }

  private boolean retryable(LinkUpJobResponse response, OfflineExecutionStatus status) {
    if (status != OfflineExecutionStatus.FAILED) return false;
    String code = response == null ? null : response.getErrorCode();
    if (!StringUtils.hasText(code)) return true;
    String normalized = code.toUpperCase(java.util.Locale.ROOT);
    return !(normalized.contains("CONFIG")
        || normalized.contains("VALIDATION")
        || normalized.contains("IDEMPOTENCY")
        || normalized.contains("BAD_REQUEST")
        || normalized.contains("UNSUPPORTED"));
  }

  private JsonNode readJobSpec(String value) {
    if (!StringUtils.hasText(value)) throw new IllegalStateException("任务缺少 Link-Up JobSpec");
    try {
      JsonNode node = objectMapper.readTree(value);
      if (node == null || !node.isObject()) {
        throw new IllegalStateException("Link-Up JobSpec 不是 JSON 对象");
      }
      return node;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Link-Up JobSpec 已损坏", exception);
    }
  }

  private void record(
      OfflineJobExecution execution,
      String from,
      String to,
      String type,
      String message,
      String payload) {
    eventRepository.append(
        OfflineExecutionEvent.builder()
            .executionId(execution.getId())
            .stateVersion(value(execution.getStateVersion(), 0L))
            .fromStatus(from)
            .toStatus(to)
            .eventType(type)
            .message(message)
            .payloadJson(payload)
            .createTime(LocalDateTime.now())
            .build());
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化 Link-Up 执行快照失败", exception);
    }
  }

  private long number(JsonNode node, String field, long fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || !value.isNumber() ? fallback : value.asLong(fallback);
  }

  private double decimal(JsonNode node, String field, double fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || !value.isNumber() ? fallback : value.asDouble(fallback);
  }

  private LocalDateTime time(Long millis) {
    return millis == null || millis <= 0L
        ? null
        : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
  }

  private String first(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }
}
