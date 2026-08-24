package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpRequestException;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.execution.adapter.OfflineBatchScopeExecutionAdapter;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Coordinates claim -> frozen scope -> Link-Up submit -> Attempt state application. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionCoordinator {

  private final OfflineJobDefinitionService definitionService;
  private final OfflineExecutionClaimManager claimManager;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineBatchRuntime batchRuntime;
  private final OfflineBatchScopeExecutionAdapter scopeExecutionAdapter;
  private final OfflineExecutionStateManager stateManager;
  private final LinkUpClient linkUpClient;
  private final ObjectMapper objectMapper;

  public OfflineExecutionCoordinator(
      OfflineJobDefinitionService definitionService,
      OfflineExecutionClaimManager claimManager,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineBatchRuntime batchRuntime,
      OfflineBatchScopeExecutionAdapter scopeExecutionAdapter,
      OfflineExecutionStateManager stateManager,
      LinkUpClient linkUpClient,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.definitionService = definitionService;
    this.claimManager = claimManager;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.batchRuntime = batchRuntime;
    this.scopeExecutionAdapter = scopeExecutionAdapter;
    this.stateManager = stateManager;
    this.linkUpClient = linkUpClient;
    this.objectMapper = objectMapper;
  }

  public OfflineJobExecution execute(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    OfflineExecutionClaim claim =
        claimManager.claim(definitionId, triggerType, retryFromExecutionId, attemptNo);
    return submitClaim(claim);
  }

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

  public OfflineJobExecution executeSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String idempotencyKey) {
    OfflineExecutionClaim claim =
        claimManager.claimSnapshot(
            definitionId,
            definitionVersion,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson,
            BatchTriggerToken.WORKFLOW,
            idempotencyKey);
    return submitClaim(claim);
  }

  public OfflineJobExecution executePendingBackfill(Long batchId) {
    return submitClaimIfNeeded(claimManager.claimPendingBackfill(batchId));
  }

  public OfflineJobExecution retryFrom(OfflineJobExecution previous) {
    if (previous == null || previous.getId() == null) {
      throw new IllegalArgumentException("重试来源实例不能为空");
    }
    return submitClaimIfNeeded(claimManager.claimRetry(previous.getId()));
  }

  public OfflineJobExecution cancelLatestBatch(Long definitionId) {
    BatchExecution batch = batchRuntime.requireLatestOccupyingBatch(definitionId);
    if (batch.status() == BatchStatus.WAITING_RETRY) {
      OfflineJobExecution latest = batchRuntime.cancelWaitingRetry(batch);
      stateManager.markWaitingRetryCanceled(latest);
      return latest;
    }
    return cancel(batchRuntime.requireLatestAttempt(batch).getId());
  }

  public OfflineJobExecution cancel(Long id) {
    OfflineJobExecution execution = require(id);
    ensureLatestAttemptForCancel(execution);
    if (!OfflineExecutionStatus.isActive(execution.getStatus())) {
      throw new IllegalStateException("当前执行实例已结束，无需停止");
    }

    stateManager.markCancellationRequested(execution);
    if (StringUtils.hasText(execution.getEngineJobId())) {
      stateManager.applySnapshot(
          execution,
          linkUpClient.cancel(execution.getEngineJobId()),
          "CANCEL_ACCEPTED");
    }
    return execution;
  }

  public void applySnapshot(
      OfflineJobExecution execution,
      LinkUpJobResponse response,
      String eventType) {
    stateManager.applySnapshot(execution, response, eventType);
  }

  public void markUnknown(OfflineJobExecution execution, String message) {
    stateManager.markUnknown(execution, message);
  }

  public OfflineJobExecution require(Long id) {
    if (id == null || id <= 0L) {
      throw new IllegalArgumentException("任务实例 ID 不合法");
    }
    return executionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务实例不存在：" + id));
  }

  private OfflineJobExecution submitClaimIfNeeded(OfflineExecutionClaim claim) {
    OfflineJobExecution execution = claim.getExecution();
    if (claim.isReused() && !OfflineExecutionStatus.isCreated(execution.getStatus())) {
      return execution;
    }
    return submitClaim(claim);
  }

  private OfflineJobExecution submitClaim(OfflineExecutionClaim claim) {
    String executionJobSpec = resolveScopedExecutionJobSpec(claim);
    OfflineJobExecution execution = claim.getExecution();
    stateManager.recordCreated(execution);

    try {
      LinkUpNodeResponse node = linkUpClient.node();
      stateManager.bindWorker(execution, node.getInstanceId());

      JsonNode jobSpec = readJobSpec(executionJobSpec);
      stateManager.markSubmitting(execution);
      LinkUpJobResponse response =
          linkUpClient.submit(
              execution.getExternalExecutionId(),
              execution.getIdempotencyKey(),
              execution.getDefinitionVersion(),
              jobSpec);
      stateManager.applySnapshot(execution, response, "SUBMITTED");
      return execution;
    } catch (LinkUpRequestException exception) {
      boolean retryable = exception.getStatusCode() == 429 || exception.getStatusCode() >= 500;
      stateManager.markFailed(
          execution,
          exception.getCode() + "：" + exception.getMessage(),
          retryable);
      throw exception;
    } catch (LinkUpTransportException exception) {
      if (exception.isUncertain()) {
        stateManager.markSubmitUncertain(execution, exception.getMessage());
        return execution;
      }
      stateManager.markFailed(execution, exception.getMessage(), true);
      throw exception;
    } catch (RuntimeException exception) {
      stateManager.markFailed(execution, exception.getMessage(), false);
      throw exception;
    }
  }

  private String resolveScopedExecutionJobSpec(OfflineExecutionClaim claim) {
    OfflineJobExecution execution = claim.getExecution();
    String logicalJobSpec = claim.getLogicalJobSpecJson();
    Long batchId = execution.getBatchId();
    if (batchId != null && batchId > 0L) {
      BatchExecution batch =
          batchRepository
              .findById(batchId)
              .orElseThrow(
                  () -> new IllegalStateException("Attempt 绑定的 BatchExecution 不存在：" + batchId));
      logicalJobSpec =
          scopeExecutionAdapter.apply(
              batch.taskId(),
              logicalJobSpec,
              batch.batchScope());
    }
    return definitionService.resolveExecutionJobSpec(logicalJobSpec);
  }

  private void ensureLatestAttemptForCancel(OfflineJobExecution execution) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException("历史执行未绑定 Batch，仅支持查询，不能执行取消命令");
    }

    BatchExecution batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("Attempt 绑定的 BatchExecution 不存在：" + batchId));
    Long latestId = batch.latestAttempt().map(attempt -> attempt.id()).orElse(null);
    if (!Objects.equals(latestId, execution.getId())) {
      throw new IllegalStateException("只能停止 Batch 的 latest Attempt");
    }
  }

  private JsonNode readJobSpec(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("任务缺少 Link-Up JobSpec");
    }
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
}
