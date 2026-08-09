package io.yak.ops.business.sync.offline.service;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionControlRepository;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 固定 Link-Up 地址下原子创建执行实例。 */
@ConditionalOnOfflineSyncEnabled
@Service
public class OfflineExecutionClaimService {
  private final OfflineJobDefinitionService definitionService;
  private final OfflineJobExecutionDao executionDao;
  private final OfflineExecutionControlRepository repository;
  private final OfflineSyncProperties properties;

  public OfflineExecutionClaimService(
      OfflineJobDefinitionService definitionService,
      OfflineJobExecutionDao executionDao,
      OfflineExecutionControlRepository repository,
      OfflineSyncProperties properties) {
    this.definitionService = definitionService;
    this.executionDao = executionDao;
    this.repository = repository;
    this.properties = properties;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public ClaimResult claim(
      Long definitionId,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo) {
    repository.lockDefinition(definitionId);
    OfflineJobDefinitionPO definition = definitionService.require(definitionId);
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("请先上线任务，再执行运行操作");
    }
    String logicalJobSpec = definitionService.resolveLogicalJobSpec(definition);
    return createClaim(
        definition,
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        definition.getConfigDigest(),
        definition.getDefinitionJson(),
        logicalJobSpec,
        triggerType,
        retryFromExecutionId,
        attemptNo,
        null);
  }

  /**
   * 按工作流发布时保存的任务快照创建执行。
   *
   * <p>这里不要求任务当前仍处于 ONLINE，也不要求当前 version 与快照一致；工作流发布版本
   * 已经完成过可执行性校验。仍对任务定义行加锁并检查活跃实例，沿用现有同步任务并发保护。</p>
   */
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
    repository.lockDefinition(definitionId);
    OfflineJobDefinitionPO current = definitionService.require(definitionId);
    return createClaim(
        current,
        Math.max(1L, definitionVersion),
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        triggerType,
        null,
        1,
        idempotencyKey);
  }

  private ClaimResult createClaim(
      OfflineJobDefinitionPO definition,
      long definitionVersion,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String triggerType,
      Long retryFromExecutionId,
      int attemptNo,
      String idempotencyKey) {
    Long definitionId = definition.getId();
    if (repository.hasActiveExecution(definitionId)) {
      throw new IllegalStateException("任务已有运行中的执行实例，不能重复提交");
    }

    LocalDateTime now = LocalDateTime.now();
    OfflineJobExecutionPO execution = new OfflineJobExecutionPO();
    execution.setJobDefinitionId(definitionId);
    execution.setDefinitionVersion((int) Math.min(Integer.MAX_VALUE, definitionVersion));
    execution.setEngineBaseUrl(properties.getEngine().getBaseUrl());
    execution.setExternalExecutionId("yak-offline-" + UUID.randomUUID());
    execution.setIdempotencyKey(
        StringUtils.hasText(idempotencyKey)
            ? idempotencyKey.trim()
            : UUID.randomUUID().toString());
    execution.setStatus(OfflineExecutionStatus.CREATED.name());
    execution.setStateVersion(1L);
    execution.setAttemptNo(Math.max(1, attemptNo));
    execution.setTriggerType(triggerType);
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
    if (!executionDao.insert(execution) || execution.getId() == null) {
      throw new IllegalStateException("创建离线同步执行实例失败");
    }
    return new ClaimResult(definition, logicalJobSpecJson, execution);
  }

  public static final class ClaimResult {
    private final OfflineJobDefinitionPO definition;
    private final String logicalJobSpecJson;
    private final OfflineJobExecutionPO execution;

    public ClaimResult(
        OfflineJobDefinitionPO definition,
        String logicalJobSpecJson,
        OfflineJobExecutionPO execution) {
      this.definition = definition;
      this.logicalJobSpecJson = logicalJobSpecJson;
      this.execution = execution;
    }

    public OfflineJobDefinitionPO getDefinition() {
      return definition;
    }

    public String getLogicalJobSpecJson() {
      return logicalJobSpecJson;
    }

    public OfflineJobExecutionPO getExecution() {
      return execution;
    }
  }
}
