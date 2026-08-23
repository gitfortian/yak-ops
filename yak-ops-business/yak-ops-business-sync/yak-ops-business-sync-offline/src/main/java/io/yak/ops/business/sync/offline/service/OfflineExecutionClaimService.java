package io.yak.ops.business.sync.offline.service;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.domain.compat.LegacyBatchTriggerCompatibilityMapper;
import io.yak.ops.business.sync.offline.domain.compat.LegacyBatchTriggerCompatibilityMapper.Mapping;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 固定 Link-Up 地址下原子创建执行实例。 */
@ConditionalOnOfflineSyncEnabled
@Service
public class OfflineExecutionClaimService {
  private final OfflineJobDefinitionService definitionService;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineSyncProperties properties;

  public OfflineExecutionClaimService(
      OfflineJobDefinitionService definitionService,
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineSyncProperties properties) {
    this.definitionService = definitionService;
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.scheduleRepository = scheduleRepository;
    this.properties = properties;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claim(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    definitionRepository.lock(definitionId);
    OfflineJobDefinition definition = definitionService.require(definitionId);
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("请先上线任务，再执行运行操作");
    }
    String logicalJobSpec = definitionService.resolveLogicalJobSpec(definition);
    Mapping trigger = LegacyBatchTriggerCompatibilityMapper.parse(triggerType);
    return createClaim(
        definition,
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        definition.getConfigDigest(),
        definition.getDefinitionJson(),
        logicalJobSpec,
        trigger,
        retryFromExecutionId,
        attemptNo,
        null);
  }

  /** 工作流按发布时保存的任务快照创建执行，不要求当前任务仍处于 ONLINE。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claimSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String triggerType) {
    return claimSnapshot(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        triggerType,
        null);
  }

  /** 工作流 Attempt 级快照执行；idempotencyKey 通常直接使用 workflow attemptId。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claimSnapshot(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String triggerType,
      String idempotencyKey) {
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalArgumentException("任务版本快照缺少 JobSpec");
    }
    definitionRepository.lock(definitionId);
    OfflineJobDefinition current = definitionService.require(definitionId);
    String normalizedKey = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
    if (normalizedKey != null) {
      OfflineJobExecution existing = executionRepository.findByIdempotencyKey(normalizedKey).orElse(null);
      if (existing != null) {
        validateIdempotentReuse(
            existing,
            definitionId,
            definitionVersion,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson);
        return new ClaimResult(current, logicalJobSpecJson, existing, true);
      }
    }
    return createClaim(
        current,
        Math.max(1L, definitionVersion),
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        LegacyBatchTriggerCompatibilityMapper.parse(triggerType),
        null,
        1,
        normalizedKey);
  }

  private ClaimResult createClaim(
      OfflineJobDefinition definition,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      Mapping trigger,
      Long retryFromExecutionId,
      int attemptNo,
      String idempotencyKey) {
    Long definitionId = definition.getId();
    if (executionRepository.hasActiveExecution(definitionId)) {
      throw new IllegalStateException("任务已有运行中的执行实例，不能重复提交");
    }

    int normalizedAttemptNo = Math.max(1, attemptNo);
    String normalizedIdempotencyKey =
        StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : UUID.randomUUID().toString();
    Long batchId = resolveBatchId(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        trigger,
        retryFromExecutionId,
        normalizedAttemptNo,
        normalizedIdempotencyKey);

    LocalDateTime now = LocalDateTime.now();
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setJobDefinitionId(definitionId);
    execution.setBatchId(batchId);
    execution.setDefinitionVersion((int) Math.min(Integer.MAX_VALUE, definitionVersion));
    execution.setEngineBaseUrl(properties.getEngine().getBaseUrl());
    execution.setExternalExecutionId("yak-offline-" + UUID.randomUUID());
    execution.setIdempotencyKey(normalizedIdempotencyKey);
    execution.setStatus(OfflineExecutionStatus.CREATED.name());
    execution.setStateVersion(1L);
    execution.setAttemptNo(normalizedAttemptNo);
    execution.setTriggerType(trigger.legacyTriggerType());
    execution.setRetryFromExecutionId(retryFromExecutionId);
    execution.setCancellationRequested(false);
    execution.setRetryCreated(false);
    execution.setConfigDigest(configDigest);
    execution.setDefinitionSnapshotJson(definitionSnapshotJson);
    execution.setSubmittedConfig(logicalJobSpecJson);
    execution.setSourceRecordCount(0L);
    execution.setSinkSuccessRecordCount(0L);
    execution.setSourceReadBytes(0L);
    execution.setSinkWrittenBytes(0L);
    execution.setQps(0D);
    execution.setDurationMillis(0L);
    execution.setCreateTime(now);
    execution.setUpdateTime(now);
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建离线同步执行实例失败");
    }
    return new ClaimResult(definition, logicalJobSpecJson, execution, false);
  }

  private Long resolveBatchId(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      Mapping trigger,
      Long retryFromExecutionId,
      int attemptNo,
      String idempotencyKey) {
    if (retryFromExecutionId != null || attemptNo > 1 || "RETRY".equalsIgnoreCase(trigger.legacyTriggerType())) {
      if (retryFromExecutionId == null) return null;
      return executionRepository.findById(retryFromExecutionId)
          .map(OfflineJobExecution::getBatchId)
          .orElse(null);
    }

    BatchScope scope = BatchScope.fullSelection();
    BatchTrigger batchTrigger = Objects.requireNonNull(trigger.batchTrigger(), "初始执行必须包含 BatchTrigger");
    BatchKey batchKey = trigger.batchKey();
    if (batchKey == null) {
      batchKey = switch (batchTrigger) {
        case MANUAL -> BatchKey.manual(idempotencyKey);
        case WORKFLOW -> BatchKey.workflow(idempotencyKey);
        case BACKFILL -> BatchKey.backfill(idempotencyKey, scope.fingerprint());
        case SCHEDULE -> throw new IllegalArgumentException(
            "SCHEDULE trigger 必须携带 scheduleId + plannedFireTime");
      };
    }

    RetryPolicySnapshot retryPolicy = freezeRetryPolicy(definitionId);
    ExecutionSnapshot snapshot = new ExecutionSnapshot(
        requireText(definitionSnapshotJson, "definitionSnapshot 不能为空"),
        (int) Math.min(Integer.MAX_VALUE, Math.max(1L, definitionVersion)),
        retryPolicy,
        requireText(configDigest, "configDigest 不能为空"));
    BatchExecution batch = new BatchExecution(
        null,
        definitionId,
        batchKey,
        batchTrigger,
        scope,
        snapshot,
        BatchStatus.PENDING,
        java.util.List.of());
    BatchExecution saved = batchRepository.insert(batch);
    if (saved.id() == null) throw new IllegalStateException("创建 BatchExecution 后缺少 ID");
    return saved.id();
  }

  private RetryPolicySnapshot freezeRetryPolicy(Long definitionId) {
    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    int maxAttempts = schedule == null
        ? properties.getControl().getDefaultMaxAttempts()
        : schedule.retryMaxAttempts();
    int backoffSeconds = schedule == null
        ? properties.getControl().getDefaultRetryBackoffSeconds()
        : schedule.retryBackoffSeconds();
    return new RetryPolicySnapshot(Math.max(1, maxAttempts), Math.max(0, backoffSeconds));
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalStateException(message);
    return value.trim();
  }

  private void validateIdempotentReuse(
      OfflineJobExecution existing,
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    int version = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, definitionVersion));
    boolean same = Objects.equals(existing.getJobDefinitionId(), definitionId)
        && Objects.equals(existing.getDefinitionVersion(), version)
        && Objects.equals(existing.getConfigDigest(), configDigest)
        && Objects.equals(existing.getDefinitionSnapshotJson(), definitionSnapshotJson)
        && Objects.equals(existing.getSubmittedConfig(), logicalJobSpecJson);
    if (!same) {
      throw new IllegalStateException("幂等键已被不同任务执行占用：" + existing.getIdempotencyKey());
    }
  }

  public static final class ClaimResult {
    private final OfflineJobDefinition definition;
    private final String logicalJobSpecJson;
    private final OfflineJobExecution execution;
    private final boolean reused;

    public ClaimResult(
        OfflineJobDefinition definition,
        String logicalJobSpecJson,
        OfflineJobExecution execution) {
      this(definition, logicalJobSpecJson, execution, false);
    }

    public ClaimResult(
        OfflineJobDefinition definition,
        String logicalJobSpecJson,
        OfflineJobExecution execution,
        boolean reused) {
      this.definition = definition;
      this.logicalJobSpecJson = logicalJobSpecJson;
      this.execution = execution;
      this.reused = reused;
    }

    public OfflineJobDefinition getDefinition() { return definition; }
    public String getLogicalJobSpecJson() { return logicalJobSpecJson; }
    public OfflineJobExecution getExecution() { return execution; }
    public boolean isReused() { return reused; }
  }
}
