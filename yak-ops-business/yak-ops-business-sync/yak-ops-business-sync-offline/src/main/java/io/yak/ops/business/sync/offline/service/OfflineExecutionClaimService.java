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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 固定 Link-Up 地址下原子创建 ExecutionAttempt。 */
@ConditionalOnOfflineSyncEnabled
@Service
public class OfflineExecutionClaimService {
  private final OfflineJobDefinitionService definitionService;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineBatchRuntimeService batchRuntimeService;
  private final OfflineSyncProperties properties;

  public OfflineExecutionClaimService(
      OfflineJobDefinitionService definitionService,
      OfflineJobDefinitionRepository definitionRepository,
      OfflineJobExecutionRepository executionRepository,
      OfflineBatchExecutionRepository batchRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineBatchRuntimeService batchRuntimeService,
      OfflineSyncProperties properties) {
    this.definitionService = definitionService;
    this.definitionRepository = definitionRepository;
    this.executionRepository = executionRepository;
    this.batchRepository = batchRepository;
    this.scheduleRepository = scheduleRepository;
    this.batchRuntimeService = batchRuntimeService;
    this.properties = properties;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claim(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    Mapping trigger = LegacyBatchTriggerCompatibilityMapper.parse(triggerType);
    if (retryFromExecutionId != null
        || attemptNo > 1
        || "RETRY".equalsIgnoreCase(trigger.legacyTriggerType())) {
      throw new IllegalArgumentException("Retry 必须通过 Batch 内 claimRetry 创建新 Attempt");
    }
    definitionRepository.lock(definitionId);
    OfflineJobDefinition definition = definitionService.require(definitionId);
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("请先上线任务，再执行运行操作");
    }
    String logicalJobSpec = definitionService.resolveLogicalJobSpec(definition);
    return createInitialClaim(
        definition,
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        definition.getConfigDigest(),
        definition.getDefinitionJson(),
        logicalJobSpec,
        trigger,
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
    Mapping trigger = LegacyBatchTriggerCompatibilityMapper.parse(triggerType);
    if ("RETRY".equalsIgnoreCase(trigger.legacyTriggerType())) {
      throw new IllegalArgumentException("Retry 不能通过 claimSnapshot 创建新 Batch");
    }
    definitionRepository.lock(definitionId);
    OfflineJobDefinition current = definitionService.require(definitionId);
    String normalizedKey = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
    if (normalizedKey != null) {
      OfflineJobExecution existing = executionRepository.findByIdempotencyKey(normalizedKey).orElse(null);
      if (existing != null) {
        BatchExecution existingBatch = validateIdempotentReuse(
            existing,
            definitionId,
            definitionVersion,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson);
        return new ClaimResult(current, existingBatch.snapshot().logicalJobSpec(), existing, true);
      }
    }
    return createInitialClaim(
        current,
        Math.max(1L, definitionVersion),
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        trigger,
        normalizedKey);
  }

  /**
   * 在原 Batch 内创建下一次 Retry Attempt。
   *
   * <p>Retry 只读取 Batch 自己冻结的 Snapshot / RetryPolicy / logical JobSpec，不回读 current
   * Task 或 current SchedulePolicy。
   */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claimRetry(Long retryFromExecutionId) {
    if (retryFromExecutionId == null || retryFromExecutionId <= 0L) {
      throw new IllegalArgumentException("重试来源实例不能为空");
    }
    OfflineJobExecution previous = executionRepository.findById(retryFromExecutionId)
        .orElseThrow(() -> new IllegalArgumentException("重试来源实例不存在：" + retryFromExecutionId));
    Long batchId = previous.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException("旧执行实例未绑定 Batch，仅支持历史查询，不能按领域规则 Retry");
    }

    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("Retry 所属 BatchExecution 不存在：" + batchId));
    if (!Objects.equals(previous.getJobDefinitionId(), batch.taskId())) {
      throw new IllegalStateException("Retry 来源 Attempt 与 Batch 的 Task 不一致");
    }
    if (batch.status().isTerminal()) {
      throw new IllegalStateException("Batch 已进入终态，不能追加 Retry Attempt");
    }

    OfflineExecutionStatus previousStatus = parseStatus(previous.getStatus());
    if (previousStatus == OfflineExecutionStatus.UNKNOWN) {
      throw new IllegalStateException("执行结果为 UNKNOWN，必须先 reconcile，禁止盲目 Retry");
    }
    if (previousStatus != OfflineExecutionStatus.FAILED) {
      throw new IllegalStateException("只有明确 FAILED 的 Attempt 才能 Retry");
    }

    List<OfflineJobExecution> attempts = executionRepository.findByBatchId(batchId);
    if (attempts.isEmpty()) {
      throw new IllegalStateException("Batch 缺少 Attempt 历史，不能 Retry");
    }
    int previousAttemptNo = positive(previous.getAttemptNo(), "attemptNo");
    int nextAttemptNo = previousAttemptNo + 1;

    OfflineJobExecution existingNext = attempts.stream()
        .filter(attempt -> value(attempt.getAttemptNo(), 1) == nextAttemptNo)
        .filter(attempt -> Objects.equals(attempt.getRetryFromExecutionId(), previous.getId()))
        .findFirst()
        .orElse(null);
    if (existingNext != null) {
      batchRuntimeService.refreshBatch(batchId);
      return new ClaimResult(null, batch.snapshot().logicalJobSpec(), existingNext, true);
    }

    OfflineJobExecution latest = attempts.stream()
        .max(
            Comparator.comparingInt((OfflineJobExecution attempt) -> value(attempt.getAttemptNo(), 1))
                .thenComparingLong(attempt -> value(attempt.getId(), 0L)))
        .orElseThrow(() -> new IllegalStateException("Batch 缺少最新 Attempt"));
    if (!Objects.equals(latest.getId(), previous.getId())) {
      throw new IllegalStateException("只能从 Batch 最新 Attempt 创建 Retry");
    }

    RetryPolicySnapshot retryPolicy = batch.snapshot().retryPolicy();
    if (nextAttemptNo > retryPolicy.maxAttempts()) {
      throw new IllegalStateException("Retry 已达到 Batch 冻结的最大 Attempt 数");
    }

    if (!executionRepository.reserveRetry(previous.getId())) {
      throw new IllegalStateException("Retry 已被其他请求保留或已经创建");
    }

    OfflineJobExecution execution = newAttempt(
        batch,
        nextAttemptNo,
        "RETRY",
        previous.getId(),
        retryIdempotencyKey(batch.id(), nextAttemptNo));
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建 Retry Attempt 失败");
    }
    batchRuntimeService.refreshBatch(batchId);
    return new ClaimResult(null, batch.snapshot().logicalJobSpec(), execution, false);
  }

  /**
   * Wave 5 Backfill dispatcher claim：从已经物化的 PENDING Batch 创建 Attempt 1。
   *
   * <p>Batch Snapshot 是唯一配置来源；PENDING -> RUNNING reservation 与 Attempt insert 同事务。
   */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claimPendingBackfill(Long batchId) {
    if (batchId == null || batchId <= 0L) {
      throw new IllegalArgumentException("BatchExecutionId 必须大于 0");
    }
    BatchExecution initial = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalArgumentException("Backfill BatchExecution 不存在：" + batchId));
    if (initial.trigger() != BatchTrigger.BACKFILL) {
      throw new IllegalStateException("只有 BACKFILL Batch 可以通过 pending dispatcher 创建 Attempt 1");
    }

    definitionRepository.lock(initial.taskId());
    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("Backfill BatchExecution 已不存在：" + batchId));
    List<OfflineJobExecution> existingAttempts = executionRepository.findByBatchId(batchId);
    OfflineJobExecution existingInitial = existingAttempts.stream()
        .filter(attempt -> value(attempt.getAttemptNo(), 1) == 1)
        .findFirst()
        .orElse(null);
    if (existingInitial != null) {
      batchRuntimeService.refreshBatch(batchId);
      return new ClaimResult(null, batch.snapshot().logicalJobSpec(), existingInitial, true);
    }
    if (batch.status() != BatchStatus.PENDING) {
      throw new IllegalStateException("Backfill Batch 已不处于 PENDING：" + batch.status());
    }
    if (batchRuntimeService.hasOccupyingBatch(batch.taskId())) {
      throw new IllegalStateException("Task 已有 occupying Batch，Backfill 保持排队");
    }
    if (!batchRepository.reservePendingBackfill(batchId)) {
      throw new IllegalStateException("Backfill Batch 已被其他 dispatcher reservation");
    }

    OfflineJobExecution execution = newAttempt(
        batch,
        1,
        "BACKFILL",
        null,
        "offline-backfill:" + batchId + ":1");
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建 Backfill Attempt 1 失败");
    }
    batchRuntimeService.refreshBatch(batchId);
    return new ClaimResult(null, batch.snapshot().logicalJobSpec(), execution, false);
  }

  private ClaimResult createInitialClaim(
      OfflineJobDefinition definition,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      Mapping trigger,
      String idempotencyKey) {
    Long definitionId = definition.getId();
    if (batchRuntimeService.hasOccupyingBatch(definitionId)) {
      throw new IllegalStateException("任务已有运行中的 BatchExecution，不能重复提交");
    }

    String normalizedIdempotencyKey =
        StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : UUID.randomUUID().toString();
    Long batchId = createBatch(
        definitionId,
        definitionVersion,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        trigger,
        normalizedIdempotencyKey);
    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("创建后无法读取 BatchExecution：" + batchId));

    OfflineJobExecution execution = newAttempt(
        batch,
        1,
        trigger.legacyTriggerType(),
        null,
        normalizedIdempotencyKey);
    if (!executionRepository.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建离线同步执行实例失败");
    }
    batchRuntimeService.refreshBatch(batchId);
    return new ClaimResult(definition, batch.snapshot().logicalJobSpec(), execution, false);
  }

  private Long createBatch(
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      Mapping trigger,
      String idempotencyKey) {
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
        requireText(configDigest, "configDigest 不能为空"),
        requireText(logicalJobSpecJson, "logicalJobSpec 不能为空"));
    BatchExecution batch = new BatchExecution(
        null,
        definitionId,
        batchKey,
        batchTrigger,
        scope,
        snapshot,
        BatchStatus.PENDING,
        List.of());
    BatchExecution saved = batchRepository.insert(batch);
    if (saved.id() == null) throw new IllegalStateException("创建 BatchExecution 后缺少 ID");
    return saved.id();
  }

  private OfflineJobExecution newAttempt(
      BatchExecution batch,
      int attemptNo,
      String triggerType,
      Long retryFromExecutionId,
      String idempotencyKey) {
    LocalDateTime now = LocalDateTime.now();
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setJobDefinitionId(batch.taskId());
    execution.setBatchId(batch.id());
    execution.setDefinitionVersion(batch.snapshot().definitionRevision());
    execution.setEngineBaseUrl(properties.getEngine().getBaseUrl());
    execution.setExternalExecutionId("yak-offline-" + UUID.randomUUID());
    execution.setIdempotencyKey(requireText(idempotencyKey, "idempotencyKey 不能为空"));
    execution.setStatus(OfflineExecutionStatus.CREATED.name());
    execution.setStateVersion(1L);
    execution.setAttemptNo(Math.max(1, attemptNo));
    execution.setTriggerType(requireText(triggerType, "triggerType 不能为空"));
    execution.setRetryFromExecutionId(retryFromExecutionId);
    execution.setCancellationRequested(false);
    execution.setRetryCreated(false);
    // Legacy persistence compatibility copies. Batch.snapshot() remains the runtime truth.
    execution.setConfigDigest(batch.snapshot().configDigest());
    execution.setDefinitionSnapshotJson(batch.snapshot().definitionSnapshot());
    execution.setSubmittedConfig(batch.snapshot().logicalJobSpec());
    execution.setSourceRecordCount(0L);
    execution.setSinkSuccessRecordCount(0L);
    execution.setSourceReadBytes(0L);
    execution.setSinkWrittenBytes(0L);
    execution.setQps(0D);
    execution.setDurationMillis(0L);
    execution.setCreateTime(now);
    execution.setUpdateTime(now);
    return execution;
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

  private String retryIdempotencyKey(Long batchId, int attemptNo) {
    return "offline-retry:" + batchId + ":" + attemptNo;
  }

  private OfflineExecutionStatus parseStatus(String status) {
    try {
      return OfflineExecutionStatus.parse(status);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Retry 来源 Attempt 状态不合法：" + status, exception);
    }
  }

  private int positive(Integer value, String field) {
    if (value == null || value < 1) throw new IllegalStateException(field + " 必须大于 0");
    return value;
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalStateException(message);
    return value.trim();
  }

  private int value(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private BatchExecution validateIdempotentReuse(
      OfflineJobExecution existing,
      Long definitionId,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    Long batchId = existing.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException(
          "幂等键命中 Wave 1 前历史执行，未绑定 Batch，仅支持查询：" + existing.getIdempotencyKey());
    }
    BatchExecution batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new IllegalStateException("幂等 Attempt 绑定的 BatchExecution 不存在：" + batchId));
    ExecutionSnapshot snapshot = batch.snapshot();
    int version = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, definitionVersion));
    boolean same = Objects.equals(existing.getJobDefinitionId(), batch.taskId())
        && Objects.equals(batch.taskId(), definitionId)
        && snapshot.definitionRevision() == version
        && Objects.equals(snapshot.configDigest(), configDigest)
        && Objects.equals(snapshot.definitionSnapshot(), definitionSnapshotJson)
        && Objects.equals(snapshot.logicalJobSpec(), logicalJobSpecJson);
    if (!same) {
      throw new IllegalStateException("幂等键已被不同 Batch 快照占用：" + existing.getIdempotencyKey());
    }
    return batch;
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
