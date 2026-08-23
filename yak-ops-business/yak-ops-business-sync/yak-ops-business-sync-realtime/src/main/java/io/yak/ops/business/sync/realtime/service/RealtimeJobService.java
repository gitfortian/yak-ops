package io.yak.ops.business.sync.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Application service for realtime task definitions and SyncExecution lifecycle commands. */
@Service
public class RealtimeJobService {

  private final RealtimeJobStore store;
  private final ObjectMapper json;
  private final CdcPipelineSpecValidator specValidator;
  private final SyncExecutionStateMachine executionStateMachine;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final TransactionTemplate transactions;
  private final ConcurrentHashMap<Long, ReentrantLock> lifecycleLocks = new ConcurrentHashMap<>();

  public RealtimeJobService(
      RealtimeJobStore store,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      CdcPipelineSpecValidator specValidator,
      SyncExecutionStateMachine executionStateMachine,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.json = json;
    this.specValidator = specValidator;
    this.executionStateMachine = executionStateMachine;
    this.dataSourceResolver = dataSourceResolver;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public long save(
      Long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId) {
    requireName(name);
    specValidator.validate(spec);
    runtimeResolver.environment(runtimeEnvironmentId, true);
    String sourceConfigDigest = sourceConfigDigest(spec, runtimeEnvironmentId);
    Long saved =
        transactions.execute(
            status -> {
              if (id == null) {
                long created =
                    store.insertDefinition(
                        name.trim(), description, spec, sourceConfigDigest, runtimeEnvironmentId);
                store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步草稿");
                return created;
              }
              DefinitionRow locked = store.lockDefinition(id); // cross-instance command mutex
              store.updateDefinition(
                  id,
                  name.trim(),
                  description,
                  spec,
                  sourceConfigDigest,
                  runtimeEnvironmentId);
              store.event(
                  id,
                  null,
                  "DRAFT_SAVED",
                  locked.releaseState(),
                  "DRAFT",
                  "已保存草稿并生成新定义版本；当前 SyncExecution 不受影响");
              return id;
            });
    return saved == null ? 0 : saved;
  }

  /** Creates the shell first; the editor supplies the pipeline spec in the second stage. */
  public long create(String name, String description, long runtimeEnvironmentId) {
    requireName(name);
    runtimeResolver.environment(runtimeEnvironmentId, true);
    Long saved =
        transactions.execute(
            status -> {
              long created =
                  store.insertDefinition(
                      name.trim(), description, null, null, runtimeEnvironmentId);
              store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步基础任务");
              return created;
            });
    return saved == null ? 0 : saved;
  }

  public RealtimeJobView get(long id) {
    return store.view(id);
  }

  public void publish(long id) {
    Prepared prepared = prepare(id, false);
    gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
    transactions.executeWithoutResult(
        status -> {
          DefinitionRow locked = store.lockDefinition(id); // cross-instance command mutex
          requirePreparedDefinitionCurrent(prepared, locked);
          store.publish(
              id,
              prepared.definition().draftRevision(),
              prepared.definition().sourceConfigDigest());
          store.event(
              id,
              null,
              "PUBLISHED",
              locked.releaseState(),
              "PUBLISHED",
              "Flink CDC 校验通过，当前草稿已发布；已有 SyncExecution 继续运行原 DefinitionVersion");
        });
  }

  public RealtimeEngineGateway.ValidationResult validate(long id) {
    Prepared prepared = prepare(id, false);
    return gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
  }

  public RealtimeJobView.Deployment start(long id, String requestedKey) {
    ReentrantLock lock = lifecycleLock(id);
    lock.lock();
    try {
      String key = normalizeKey(requestedKey);
      DeploymentRow existing = idempotentDeployment(id, key);
      if (existing != null) return store.deploymentView(existing);

      PublishedDefinitionRow target = requirePublishedDefinition(id);
      StartPrepared prepared = prepareVersion(id, target);
      gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
      return startPreparedLocked(id, key, prepared, true, StartIntent.START);
    } finally {
      lock.unlock();
    }
  }

  private RealtimeJobView.Deployment startPreparedLocked(
      long id,
      String key,
      StartPrepared prepared,
      boolean requireCurrentPublished,
      StartIntent intent) {
    DeploymentRow existing = idempotentDeployment(id, key);
    if (existing != null) return store.deploymentView(existing);

    StartReservation reservation;
    try {
      reservation =
          transactions.execute(
              status -> {
                DefinitionRow locked = store.lockDefinition(id); // mutex, not lifecycle truth

                DeploymentRow duplicate = idempotentDeployment(id, key);
                if (duplicate != null) {
                  return new StartReservation(duplicate.id(), false);
                }

                PublishedDefinitionRow currentTarget;
                if (requireCurrentPublished) {
                  currentTarget = requirePublishedDefinition(id);
                  requirePreparedVersionCurrent(
                      prepared, currentTarget, "已发布定义在校验期间已变化，请刷新后重试");
                } else {
                  currentTarget =
                      requireDefinitionVersion(id, prepared.definitionVersion().id());
                  requirePreparedVersionCurrent(
                      prepared,
                      currentTarget,
                      "目标 DefinitionVersion 在执行期间已变化，请刷新后重试");
                }

                DeploymentRow previousRow = store.latestDeployment(id).orElse(null);
                requireReplacementAllowsStart(previousRow, key, prepared.definitionVersion().id(), intent);
                SyncExecution previous = previousRow == null ? null : previousRow.execution();
                executionStateMachine.requireNewExecutionAllowed(previous);
                String previousDescription =
                    previous == null
                        ? ""
                        : "；上一 Execution 终态为 " + previous.observedState().name();

                long created =
                    store.insertDeployment(
                        locked,
                        prepared.spec(),
                        prepared.compiled().summary(),
                        artifactDigest(prepared.compiled().yaml()),
                        prepared.runtimeEnvironment(),
                        key);
                store.bindDeploymentDefinitionVersion(
                    created,
                    prepared.definitionVersion().id(),
                    prepared.definitionVersion().sourceDraftRevision());
                store.event(
                    id,
                    created,
                    intent.eventType,
                    null,
                    "STARTING",
                    intent.messagePrefix
                        + " DefinitionVersion v"
                        + prepared.definitionVersion().versionNo()
                        + "，通过运行环境「"
                        + prepared.runtimeEnvironment().name()
                        + "」提交 Flink CDC 任务"
                        + previousDescription);
                return new StartReservation(created, true);
              });
    } catch (DuplicateKeyException exception) {
      DeploymentRow raced =
          store
              .deploymentByIdempotencyKey(key)
              .orElseThrow(() -> new IllegalStateException("幂等 Execution 冲突", exception));
      requireIdempotencyOwner(id, raced);
      return store.deploymentView(raced);
    }

    if (reservation == null) {
      throw new IllegalStateException("未能创建实时同步 Execution 启动预留");
    }
    if (!reservation.created()) {
      DeploymentRow duplicate =
          store
              .deploymentByIdempotencyKey(key)
              .orElseThrow(() -> new IllegalStateException("幂等 Execution 记录不存在"));
      requireIdempotencyOwner(id, duplicate);
      return store.deploymentView(duplicate);
    }

    RealtimeEngineGateway.DeployResult result;
    try {
      result = deploy(prepared, key);
    } catch (RealtimeEngineException exception) {
      markStartFailure(id, reservation.deploymentId(), exception);
      throw exception;
    }

    return completeStart(id, reservation.deploymentId(), prepared, result);
  }

  private void requireReplacementAllowsStart(
      DeploymentRow previous,
      String key,
      long targetDefinitionVersionId,
      StartIntent intent) {
    if (previous == null || !previous.replacementPending()) {
      return;
    }
    if (intent == StartIntent.START) {
      throw new IllegalStateException("已有 Restart/Apply 版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    if (!previous.replacementMatches(intent.name(), targetDefinitionVersionId, key)) {
      throw new IllegalStateException("已有其他版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
  }

  /**
   * Finishes a successful CLI submission without overwriting a concurrent stop request. If stop
   * won while the CLI was running, bind the returned JobId first and immediately cancel that exact
   * external execution.
   */
  private RealtimeJobView.Deployment completeStart(
      long id,
      long deploymentId,
      StartPrepared prepared,
      RealtimeEngineGateway.DeployResult result) {
    Boolean cancelAfterSubmit =
        transactions.execute(
            status -> {
              store.lockDefinition(id); // cross-instance command mutex
              DeploymentRow row = requireCurrentExecutionRow(id, deploymentId);
              SyncExecution execution = row.execution();

              if (execution.desiredState().name().equals("RUNNING")
                  && execution.observedState().name().equals("STARTING")) {
                executionStateMachine.requireTransition(execution, "RUNNING");
                store.markDeploymentRunning(
                    id,
                    deploymentId,
                    result.jobId(),
                    prepared.runtimeEnvironment().runtimeRevision());
                store.event(
                    id, deploymentId, "STARTED", "STARTING", "RUNNING", "Flink 已接受任务");
                return false;
              }

              String from = execution.observedState().name();
              if (!"STOPPING".equals(from)) {
                executionStateMachine.requireTransition(execution, "STOPPING");
                store.markStopping(id, deploymentId);
              }
              store.bindDeploymentForStop(
                  deploymentId, result.jobId(), prepared.runtimeEnvironment().runtimeRevision());
              store.event(
                  id,
                  deploymentId,
                  "START_CANCEL_PENDING",
                  from,
                  "STOPPING",
                  "启动提交已返回，但期间 Execution 停止意图已生效，立即取消该 Flink 任务");
              return true;
            });

    if (Boolean.TRUE.equals(cancelAfterSubmit)) {
      stopBoundJob(id, deploymentId, result.jobId(), "并发停止请求已取消刚提交的 Flink 任务");
    }
    return store.deploymentView(requireCurrentExecutionRow(id, deploymentId));
  }

  private void markStartFailure(long id, long deploymentId, RealtimeEngineException exception) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(id); // mutex
          DeploymentRow row = requireCurrentExecutionRow(id, deploymentId);
          SyncExecution execution = row.execution();
          boolean stopRequested = execution.desiredState().name().equals("STOPPED");
          String target = exception.uncertain() ? "UNKNOWN" : "FAILED";
          executionStateMachine.requireTransition(execution, target);
          store.markDeployFailure(
              id,
              deploymentId,
              exception.uncertain(),
              stopRequested,
              exception.getMessage());
          store.event(
              id,
              deploymentId,
              exception.uncertain() ? "START_UNCERTAIN" : "START_FAILED",
              execution.observedState().name(),
              target,
              exception.getMessage());
        });
  }

  public void stop(long id) {
    ReentrantLock lock = lifecycleLock(id);
    lock.lock();
    try {
      stopLocked(id);
    } finally {
      lock.unlock();
    }
  }

  private void stopLocked(long id) {
    StopReservation reservation =
        transactions.execute(
            status -> {
              store.lockDefinition(id); // mutex
              DeploymentRow deployment = store.latestDeployment(id).orElse(null);
              if (deployment == null) {
                return new StopReservation(null, true, false);
              }
              SyncExecution execution = deployment.execution();
              if (execution.terminal()) {
                return new StopReservation(deployment, true, false);
              }
              if (execution.desiredState().name().equals("STOPPED")
                  && execution.observedState().name().equals("STOPPING")) {
                return new StopReservation(deployment, false, true);
              }

              executionStateMachine.requireTransition(execution, "STOPPING");
              store.markStopping(id, deployment.id());
              store.event(
                  id,
                  deployment.id(),
                  "STOP_REQUESTED",
                  execution.observedState().name(),
                  "STOPPING",
                  "已请求停止当前 SyncExecution");
              return new StopReservation(deployment, false, false);
            });

    if (reservation == null || reservation.settled() || reservation.inProgress()) {
      return;
    }

    DeploymentRow deployment = reservation.deployment();
    if (deployment == null || !StringUtils.hasText(deployment.engineJobId())) {
      return;
    }

    stopBoundJob(id, deployment.id(), deployment.engineJobId(), "Flink 已停止任务");
  }

  private void stopBoundJob(long id, long deploymentId, String jobId, String successMessage) {
    DeploymentRow deployment = requireCurrentExecutionRow(id, deploymentId);
    ComputeEnvironmentSnapshot runtimeEnvironment = deploymentRuntime(id, deployment);
    try {
      RuntimeStatus runtime = gateway.status(runtimeEnvironment, jobId);
      if (runtime.state() == RuntimeStatus.State.RUNNING) {
        gateway.stop(runtimeEnvironment, jobId);
        waitForRuntimeStop(runtimeEnvironment, jobId);
      } else if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
        throw new RealtimeEngineException("Flink 状态未知，无法确认停止结果", true, null, null);
      }
      markStopped(id, deploymentId, jobId, successMessage);
    } catch (RealtimeEngineException exception) {
      markStopUncertain(id, deploymentId, jobId, exception.getMessage());
      throw exception;
    }
  }

  private void markStopUncertain(
      long id, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(id);
          DeploymentRow row = requireCurrentExecutionRow(id, deploymentId);
          SyncExecution execution = row.execution();
          executionStateMachine.requireTransition(execution, "UNKNOWN");
          store.reconcile(id, deploymentId, "UNKNOWN", "UNKNOWN", jobId, message);
          store.event(
              id,
              deploymentId,
              "STOP_UNCERTAIN",
              execution.observedState().name(),
              "UNKNOWN",
              message);
        });
  }

  /** Stop the current RUNNING execution and create a new execution pinned to the same version. */
  public RealtimeJobView.Deployment restartExecution(long id, String requestedKey) {
    ReentrantLock lock = lifecycleLock(id);
    lock.lock();
    try {
      String key = normalizeKey(requestedKey);
      DeploymentRow existing = idempotentDeployment(id, key);
      if (existing != null) return store.deploymentView(existing);

      DeploymentRow pending = pendingReplacement(id);
      if (pending != null) {
        return resumeReplacement(id, key, pending, StartIntent.RESTART_EXECUTION);
      }

      DeploymentRow currentRow = requireStableRunningExecutionRow(id, "重启");
      SyncExecution currentExecution = currentRow.execution();
      long targetVersionId = requireExecutionVersionId(currentExecution);
      PublishedDefinitionRow target = requireDefinitionVersion(id, targetVersionId);

      StartPrepared prepared = prepareVersion(id, target);
      gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());

      DeploymentRow reserved =
          reserveVersionReplacementStop(
              id,
              currentRow.id(),
              targetVersionId,
              key,
              StartIntent.RESTART_EXECUTION,
              "RESTART_EXECUTION_STOP_REQUESTED",
              "已为同版本 RestartExecution 预留停止当前运行实例");
      return completeReplacement(
          id,
          key,
          prepared,
          reserved,
          StartIntent.RESTART_EXECUTION,
          "当前 SyncExecution 已停止，准备按原 DefinitionVersion 重启");
    } finally {
      lock.unlock();
    }
  }

  /** Stop the current RUNNING execution and explicitly apply the PublishedDefinitionRef snapshot. */
  public RealtimeJobView.Deployment applyPublishedVersion(long id, String requestedKey) {
    ReentrantLock lock = lifecycleLock(id);
    lock.lock();
    try {
      String key = normalizeKey(requestedKey);
      DeploymentRow existing = idempotentDeployment(id, key);
      if (existing != null) return store.deploymentView(existing);

      DeploymentRow pending = pendingReplacement(id);
      if (pending != null) {
        return resumeReplacement(id, key, pending, StartIntent.APPLY_PUBLISHED_VERSION);
      }

      DeploymentRow currentRow = requireStableRunningExecutionRow(id, "应用已发布版本");
      SyncExecution currentExecution = currentRow.execution();
      long currentVersionId = requireExecutionVersionId(currentExecution);
      PublishedDefinitionRow target = requirePublishedDefinition(id); // command-time snapshot
      if (currentVersionId == target.id()) {
        throw new IllegalStateException("当前 SyncExecution 已经运行最新已发布 DefinitionVersion，无需应用");
      }

      StartPrepared prepared = prepareVersion(id, target);
      gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());

      DeploymentRow reserved =
          reserveVersionReplacementStop(
              id,
              currentRow.id(),
              target.id(),
              key,
              StartIntent.APPLY_PUBLISHED_VERSION,
              "APPLY_PUBLISHED_VERSION_STOP_REQUESTED",
              "已为 ApplyPublishedVersion 预留停止当前运行实例");
      return completeReplacement(
          id,
          key,
          prepared,
          reserved,
          StartIntent.APPLY_PUBLISHED_VERSION,
          "当前 SyncExecution 已停止，准备应用已发布 DefinitionVersion");
    } finally {
      lock.unlock();
    }
  }

  private DeploymentRow pendingReplacement(long taskId) {
    DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
    return latest != null && latest.replacementPending() ? latest : null;
  }

  private RealtimeJobView.Deployment resumeReplacement(
      long taskId, String key, DeploymentRow pending, StartIntent intent) {
    if (!Objects.equals(pending.replacementIdempotencyKey(), key)) {
      throw new IllegalStateException("已有版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    if (!Objects.equals(pending.replacementCommandType(), intent.name())) {
      throw new IllegalStateException("该 Idempotency-Key 已绑定另一种版本替换命令");
    }
    Long targetVersionId = pending.replacementTargetDefinitionVersionId();
    if (targetVersionId == null) {
      throw new IllegalStateException("待恢复的版本替换命令缺少目标 DefinitionVersionId");
    }

    PublishedDefinitionRow target = requireDefinitionVersion(taskId, targetVersionId);
    StartPrepared prepared = prepareVersion(taskId, target);
    gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
    return completeReplacement(
        taskId,
        key,
        prepared,
        pending,
        intent,
        intent == StartIntent.RESTART_EXECUTION
            ? "当前 SyncExecution 已停止，继续按原 DefinitionVersion 重启"
            : "当前 SyncExecution 已停止，继续应用已固定的 Published DefinitionVersion");
  }

  private RealtimeJobView.Deployment completeReplacement(
      long taskId,
      String key,
      StartPrepared prepared,
      DeploymentRow source,
      StartIntent intent,
      String stoppedMessage) {
    DeploymentRow latest = requireCurrentExecutionRow(taskId, source.id());
    SyncExecution execution = latest.execution();
    String observed = execution.observedState().name();

    if ("UNKNOWN".equals(observed) || "CONFLICT".equals(observed)) {
      throw new IllegalStateException("版本替换 Execution 状态不确定，请先执行状态对账");
    }
    if ("STOPPING".equals(observed)) {
      if (!StringUtils.hasText(latest.engineJobId())) {
        throw new IllegalStateException("版本替换 Execution 缺少 EngineExecutionRef，请先执行状态对账");
      }
      stopBoundJob(taskId, latest.id(), latest.engineJobId(), stoppedMessage);
    } else if (!"STOPPED".equals(observed)) {
      throw new IllegalStateException("版本替换命令只能从 STOPPING/STOPPED 状态继续，请先执行状态对账");
    }

    requireLatestExecutionSettled(taskId);
    return startPreparedLocked(taskId, key, prepared, false, intent);
  }

  public void delete(long id) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(id);
          requireDefinitionMutable(id);
          store.delete(id);
        });
  }

  public List<RealtimeJobEventView> events(long id) {
    store.definition(id)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return store.events(id);
  }

  public JsonNode capabilities(long runtimeEnvironmentId) {
    return gateway.capabilities(runtimeResolver.environment(runtimeEnvironmentId, true));
  }

  private Prepared prepare(long id, boolean requirePublished) {
    DefinitionRow definition =
        store.definition(id)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    if (requirePublished) {
      throw new IllegalArgumentException("内部调用错误：Start 必须通过 immutable Published Definition 准备");
    }
    CdcPipelineSpec spec = store.spec(definition);
    specValidator.validate(spec);
    ComputeEnvironmentSnapshot runtimeEnvironment = runtimeResolver.definition(definition, true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities(runtimeEnvironment);
    capabilityResolver.requireSupported(manifest, resolved, spec);
    return new Prepared(
        definition,
        spec,
        compiler.compile(definition.name(), spec, resolved),
        runtimeEnvironment,
        manifest);
  }

  private StartPrepared prepareVersion(long id, PublishedDefinitionRow version) {
    DefinitionRow task =
        store.definition(id)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    if (version.taskId() != id) {
      throw new IllegalStateException("DefinitionVersion 不属于当前实时同步任务");
    }
    CdcPipelineSpec spec = version.spec();
    specValidator.validate(spec);
    ComputeEnvironmentSnapshot runtimeEnvironment =
        runtimeResolver.environment(version.runtimeEnvironmentId(), true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities(runtimeEnvironment);
    capabilityResolver.requireSupported(manifest, resolved, spec);
    return new StartPrepared(
        task,
        version,
        spec,
        compiler.compile(task.name(), spec, resolved),
        runtimeEnvironment,
        manifest);
  }

  private PublishedDefinitionRow requirePublishedDefinition(long id) {
    return store
        .publishedDefinition(id)
        .orElseThrow(() -> new IllegalStateException("请先发布至少一个可运行的定义版本"));
  }

  private PublishedDefinitionRow requireDefinitionVersion(long taskId, long definitionVersionId) {
    return store
        .definitionVersion(taskId, definitionVersionId)
        .orElseThrow(
            () -> new IllegalStateException("DefinitionVersion 不存在：" + definitionVersionId));
  }

  private void requirePreparedVersionCurrent(
      StartPrepared prepared, PublishedDefinitionRow current, String message) {
    PublishedDefinitionRow snapshot = prepared.definitionVersion();
    if (snapshot.id() != current.id()
        || snapshot.taskId() != current.taskId()
        || snapshot.sourceDraftRevision() != current.sourceDraftRevision()
        || !Objects.equals(snapshot.sourceConfigDigest(), current.sourceConfigDigest())) {
      throw new IllegalStateException(message);
    }
  }

  private void requirePreparedDefinitionCurrent(Prepared prepared, DefinitionRow current) {
    DefinitionRow snapshot = prepared.definition();
    long currentRuntimeEnvironmentId = store.runtimeEnvironmentId(current.id());
    if (snapshot.draftRevision() != current.draftRevision()
        || !Objects.equals(snapshot.sourceConfigDigest(), current.sourceConfigDigest())
        || prepared.runtimeEnvironment().id() != currentRuntimeEnvironmentId) {
      throw new IllegalStateException("任务定义在校验期间已变化，请刷新后重试");
    }
  }

  private void requireDefinitionMutable(long id) {
    executionStateMachine.requireDefinitionMutable(store.latestExecution(id).orElse(null));
  }

  private DeploymentRow requireStableRunningExecutionRow(long taskId, String action) {
    DeploymentRow row =
        store.latestDeployment(taskId)
            .orElseThrow(() -> new IllegalStateException("当前没有可" + action + "的 SyncExecution"));
    if (row.replacementPending()) {
      throw new IllegalStateException("已有版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    SyncExecution execution = row.execution();
    requireStableRunningExecution(execution, action);
    if (!execution.engineExecutionRef().bound() || !StringUtils.hasText(row.engineJobId())) {
      throw new IllegalStateException(action + "要求当前 SyncExecution 已绑定 EngineExecutionRef，请先执行状态对账");
    }
    return row;
  }

  private void requireStableRunningExecution(SyncExecution execution, String action) {
    if (execution.terminal()) {
      throw new IllegalStateException("当前 SyncExecution 已结束，请直接启动已发布版本");
    }
    if (!"RUNNING".equals(execution.desiredState().name())
        || !"RUNNING".equals(execution.observedState().name())
        || execution.resultUncertain()) {
      throw new IllegalStateException(
          action
              + "只允许对稳定 RUNNING 的 SyncExecution 执行；STARTING/STOPPING/UNKNOWN/CONFLICT 请先对账");
    }
  }

  /**
   * Linearization point for RestartExecution / ApplyPublishedVersion across multiple Yak instances.
   * The target and Idempotency-Key are persisted on the source execution in the same transaction as
   * RUNNING -> STOPPING, so a STOPPED source still blocks unrelated Start and the command can resume.
   */
  private DeploymentRow reserveVersionReplacementStop(
      long taskId,
      long expectedExecutionId,
      long targetDefinitionVersionId,
      String key,
      StartIntent intent,
      String eventType,
      String message) {
    DeploymentRow reserved =
        transactions.execute(
            status -> {
              store.lockDefinition(taskId); // cross-instance command mutex
              DeploymentRow latest =
                  store.latestDeployment(taskId)
                      .orElseThrow(() -> new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试"));
              if (latest.id() != expectedExecutionId) {
                throw new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试");
              }
              SyncExecution execution = latest.execution();
              requireStableRunningExecution(execution, "版本切换");
              if (!execution.engineExecutionRef().bound() || !StringUtils.hasText(latest.engineJobId())) {
                throw new IllegalStateException("当前 SyncExecution 缺少 EngineExecutionRef，请先执行状态对账");
              }
              executionStateMachine.requireTransition(execution, "STOPPING");
              store.reserveReplacementStop(
                  taskId, latest.id(), intent.name(), targetDefinitionVersionId, key);
              store.event(
                  taskId,
                  latest.id(),
                  eventType,
                  execution.observedState().name(),
                  "STOPPING",
                  message);
              return latest;
            });
    if (reserved == null) {
      throw new IllegalStateException("未能预留版本切换停止命令");
    }
    return reserved;
  }

  private long requireExecutionVersionId(SyncExecution execution) {
    Long versionId = execution.definitionVersionId();
    if (versionId == null) {
      throw new IllegalStateException("当前 SyncExecution 缺少 DefinitionVersionRef，无法安全执行版本命令");
    }
    return versionId;
  }

  private void requireLatestExecutionSettled(long taskId) {
    SyncExecution settled = store.latestExecution(taskId).orElse(null);
    if (settled != null && settled.activeOrUncertain()) {
      throw new IllegalStateException("Execution 仍在停止中，请稍后使用相同 Idempotency-Key 重试");
    }
  }

  private DeploymentRow requireCurrentExecutionRow(long taskId, long executionId) {
    DeploymentRow latest =
        store.latestDeployment(taskId)
            .orElseThrow(() -> new IllegalStateException("SyncExecution 不存在：" + executionId));
    if (latest.id() != executionId) {
      throw new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试");
    }
    return latest;
  }

  private void waitForRuntimeStop(
      ComputeEnvironmentSnapshot runtimeEnvironment, String jobId) {
    for (int attempt = 0; attempt < 20; attempt++) {
      RuntimeStatus status = gateway.status(runtimeEnvironment, jobId);
      if (status.state() == RuntimeStatus.State.TERMINATED
          || status.state() == RuntimeStatus.State.NONE) {
        return;
      }
      if (status.state() == RuntimeStatus.State.UNKNOWN) {
        throw new RealtimeEngineException("Flink 停止状态未知，等待后续对账", true, null, null);
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new RealtimeEngineException("等待 Flink 停止时被中断", true, null, exception);
      }
    }
    throw new RealtimeEngineException("Flink 任务未在 5 秒内停止，等待后续对账", true, null, null);
  }

  private void markStopped(
      long definitionId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        status -> {
          store.lockDefinition(definitionId);
          DeploymentRow row = requireCurrentExecutionRow(definitionId, deploymentId);
          SyncExecution execution = row.execution();
          if (execution.desiredState().name().equals("STOPPED")
              && execution.observedState().name().equals("STOPPED")) {
            return;
          }
          executionStateMachine.requireTransition(execution, "STOPPED");
          store.reconcile(definitionId, deploymentId, "STOPPED", "STOPPED", jobId, null);
          store.event(
              definitionId,
              deploymentId,
              "STOPPED",
              execution.observedState().name(),
              "STOPPED",
              message);
        });
  }

  private DeploymentRow idempotentDeployment(long id, String key) {
    DeploymentRow existing = store.deploymentByIdempotencyKey(key).orElse(null);
    if (existing != null) {
      requireIdempotencyOwner(id, existing);
    }
    return existing;
  }

  private void requireIdempotencyOwner(long id, DeploymentRow deployment) {
    if (deployment.definitionId() != id) {
      throw new IllegalStateException("幂等键已被其他实时任务使用");
    }
  }

  private ReentrantLock lifecycleLock(long id) {
    return lifecycleLocks.computeIfAbsent(id, ignored -> new ReentrantLock());
  }

  private String normalizeKey(String requestedKey) {
    String key =
        StringUtils.hasText(requestedKey) ? requestedKey.trim() : UUID.randomUUID().toString();
    if (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
      throw new IllegalArgumentException("Idempotency-Key 格式无效");
    }
    return key;
  }

  private RealtimeEngineGateway.DeployResult deploy(StartPrepared prepared, String key) {
    RealtimeDeployRequest.CredentialBinding[] credentials =
        dataSourceResolver.resolveCredentials(prepared.spec());
    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            prepared.compiled().yaml(), key, credentials[0], credentials[1])) {
      return gateway.deploy(prepared.runtimeEnvironment(), request);
    }
  }

  private ComputeEnvironmentSnapshot deploymentRuntime(long id, DeploymentRow deployment) {
    if (deployment == null) {
      throw new IllegalStateException("任务尚无 Execution 记录");
    }
    DefinitionRow definition =
        store.definition(id)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return runtimeResolver.deployment(definition, deployment);
  }

  private void requireName(String name) {
    if (!StringUtils.hasText(name) || name.trim().length() > 200) {
      throw new IllegalArgumentException("任务名称不能为空且不能超过 200 个字符");
    }
  }

  private String sourceConfigDigest(CdcPipelineSpec spec, long runtimeEnvironmentId) {
    return sha256(write(spec) + "\n@runtime-environment:" + runtimeEnvironmentId);
  }

  private String artifactDigest(String compiledYaml) {
    return sha256(compiledYaml);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法序列化实时同步 Spec", exception);
    }
  }

  private String sha256(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算 SHA-256 摘要", exception);
    }
  }

  private record Prepared(
      DefinitionRow definition,
      CdcPipelineSpec spec,
      CompiledPipeline compiled,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      JsonNode manifest) {}

  private record StartPrepared(
      DefinitionRow task,
      PublishedDefinitionRow definitionVersion,
      CdcPipelineSpec spec,
      CompiledPipeline compiled,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      JsonNode manifest) {}

  private record StartReservation(long deploymentId, boolean created) {}

  private record StopReservation(
      DeploymentRow deployment, boolean settled, boolean inProgress) {}

  private enum StartIntent {
    START("START_REQUESTED", "启动"),
    RESTART_EXECUTION("RESTART_EXECUTION_REQUESTED", "重启当前运行版本"),
    APPLY_PUBLISHED_VERSION("APPLY_PUBLISHED_VERSION_REQUESTED", "应用已发布版本");

    private final String eventType;
    private final String messagePrefix;

    StartIntent(String eventType, String messagePrefix) {
      this.eventType = eventType;
      this.messagePrefix = messagePrefix;
    }
  }
}
