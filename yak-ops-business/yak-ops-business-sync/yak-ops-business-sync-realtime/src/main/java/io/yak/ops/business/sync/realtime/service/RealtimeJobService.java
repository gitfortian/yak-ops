package io.yak.ops.business.sync.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.RealtimeStateMachine;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.engine.RealtimeLogRedactor;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Application service for realtime job definitions and lifecycle commands. */
@Service
public class RealtimeJobService {

  private final RealtimeJobStore store;
  private final ObjectMapper json;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeStateMachine stateMachine;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;
  private final RealtimeLogRedactor logRedactor;
  private final TransactionTemplate transactions;
  private final RealtimeSyncProperties properties;
  private final ReentrantLock lifecycleLock = new ReentrantLock();
  private final ConcurrentHashMap<Long, AtomicInteger> consecutiveEngineFailures =
      new ConcurrentHashMap<>();

  public RealtimeJobService(
      RealtimeJobStore store,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      CdcPipelineSpecValidator specValidator,
      RealtimeStateMachine stateMachine,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeLogRedactor logRedactor,
      RealtimeSyncProperties properties,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.json = json;
    this.specValidator = specValidator;
    this.stateMachine = stateMachine;
    this.dataSourceResolver = dataSourceResolver;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
    this.logRedactor = logRedactor;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public long save(Long id, String name, String description, CdcPipelineSpec spec) {
    requireName(name);
    specValidator.validate(spec);
    String digest = digest(write(spec));
    Long saved =
        transactions.execute(
            status -> {
              if (id == null) {
                long created = store.insertDefinition(name.trim(), description, spec, digest);
                store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步草稿");
                return created;
              }
              store.updateDefinition(id, name.trim(), description, spec, digest);
              store.event(id, null, "DRAFT_SAVED", null, "DRAFT", "已保存草稿并生成新定义版本");
              return id;
            });
    return saved == null ? 0 : saved;
  }

  /** Creates the shell first; the editor supplies the pipeline spec in the second stage. */
  public long create(String name, String description) {
    requireName(name);
    Long saved =
        transactions.execute(
            status -> {
              long created = store.insertDefinition(name.trim(), description, null, null);
              store.event(created, null, "DRAFT_CREATED", null, "DRAFT", "已创建实时同步基础任务");
              return created;
            });
    return saved == null ? 0 : saved;
  }

  public RealtimeJobView get(long id) {
    return store.view(id);
  }

  public RealtimeJobPage page(int pageNo, int pageSize, String keyword) {
    return store.page(pageNo, pageSize, keyword);
  }

  public void publish(long id) {
    Prepared prepared = prepare(id, false);
    gateway.validate(prepared.compiled().yaml());
    transactions.executeWithoutResult(
        status -> {
          store.publish(id);
          store.event(id, null, "PUBLISHED", "DRAFT", "PUBLISHED", "Flink CDC 校验通过，任务已发布");
        });
  }

  public RealtimeEngineGateway.ValidationResult validate(long id) {
    Prepared prepared = prepare(id, false);
    return gateway.validate(prepared.compiled().yaml());
  }

  public RealtimeJobView.Deployment start(long id, String requestedKey) {
    lifecycleLock.lock();
    try {
      Prepared prepared = prepare(id, true);
      String key = normalizeKey(requestedKey);
      DeploymentRow existing = store.deploymentByIdempotencyKey(key).orElse(null);
      if (existing != null) {
        if (existing.definitionId() != id) {
          throw new IllegalStateException("幂等键已被其他实时任务使用");
        }
        return store.deploymentView(existing);
      }

      gateway.validate(prepared.compiled().yaml());
      Long deploymentId;
      try {
        deploymentId =
            transactions.execute(
                status -> {
                  DefinitionRow locked = store.lockDefinition(id);
                  requirePublished(locked);
                  stateMachine.requireTransition(locked.observedState(), "STARTING");
                  long created =
                      store.insertDeployment(
                          locked,
                          prepared.spec(),
                          prepared.compiled().summary(),
                          digest(prepared.compiled().yaml()),
                          key);
                  store.markStarting(id);
                  store.event(
                      id,
                      created,
                      "START_REQUESTED",
                      locked.observedState(),
                      "STARTING",
                      "开始通过 Flink CDC CLI 提交任务");
                  return created;
                });
      } catch (DuplicateKeyException exception) {
        DeploymentRow raced =
            store
                .deploymentByIdempotencyKey(key)
                .orElseThrow(() -> new IllegalStateException("幂等部署冲突", exception));
        if (raced.definitionId() != id) {
          throw new IllegalStateException("幂等键已被其他实时任务使用");
        }
        return store.deploymentView(raced);
      }

      long deployment = deploymentId == null ? 0 : deploymentId;
      try {
        RealtimeEngineGateway.DeployResult result = deploy(prepared, key);
        transactions.executeWithoutResult(
            status -> {
              store.markDeploymentRunning(
                  id, deployment, result.jobId(), prepared.runtimeRevision());
              store.event(id, deployment, "STARTED", "STARTING", "RUNNING", "Flink 已接受任务");
            });
      } catch (RealtimeEngineException exception) {
        transactions.executeWithoutResult(
            status -> {
              store.markDeployFailure(
                  id, deployment, exception.uncertain(), exception.getMessage());
              store.event(
                  id,
                  deployment,
                  exception.uncertain() ? "START_UNCERTAIN" : "START_FAILED",
                  "STARTING",
                  exception.uncertain() ? "UNKNOWN" : "FAILED",
                  exception.getMessage());
            });
        throw exception;
      }
      return store.deploymentView(
          store.latestDeployment(id).orElseThrow(() -> new IllegalStateException("部署记录不存在")));
    } finally {
      lifecycleLock.unlock();
    }
  }

  public void stop(long id) {
    lifecycleLock.lock();
    try {
      DefinitionRow definition =
          store.definition(id).orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
      DeploymentRow deployment = store.latestDeployment(id).orElse(null);
      if ("STOPPED".equals(definition.desiredState())
          && "STOPPED".equals(definition.observedState())) {
        return;
      }
      String jobId = deployment == null ? null : deployment.engineJobId();
      RuntimeStatus runtime =
          StringUtils.hasText(jobId)
              ? gateway.status(jobId)
              : new RuntimeStatus(null, RuntimeStatus.State.NONE);
      transactions.executeWithoutResult(
          status -> {
            stateMachine.requireTransition(definition.observedState(), "STOPPING");
            store.markStopping(id, deployment == null ? null : deployment.id());
            store.event(
                id,
                deployment == null ? null : deployment.id(),
                "STOP_REQUESTED",
                definition.observedState(),
                "STOPPING",
                "已请求 Flink 停止当前任务");
          });
      try {
        if (runtime.state() == RuntimeStatus.State.RUNNING) {
          gateway.stop(jobId);
          waitForRuntimeStop(jobId);
          markStopped(id, deployment, "Flink 已停止任务");
        } else {
          markStopped(id, deployment, "Flink 中已无活动任务");
        }
      } catch (RealtimeEngineException exception) {
        transactions.executeWithoutResult(
            status -> {
              store.reconcile(
                  id,
                  deployment == null ? null : deployment.id(),
                  "UNKNOWN",
                  "UNKNOWN",
                  deployment == null ? null : deployment.engineJobId(),
                  exception.getMessage());
              store.event(
                  id,
                  deployment == null ? null : deployment.id(),
                  "STOP_UNCERTAIN",
                  "STOPPING",
                  "UNKNOWN",
                  exception.getMessage());
            });
        throw exception;
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  public RealtimeJobView.Deployment restart(long id) {
    stop(id);
    return start(id, UUID.randomUUID().toString());
  }

  public void delete(long id) {
    transactions.executeWithoutResult(status -> store.delete(id));
  }

  public List<RealtimeJobEventView> events(long id) {
    store.definition(id).orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return store.events(id);
  }

  public JsonNode capabilities() {
    return gateway.capabilities();
  }

  public String logs(long id, int tail) {
    DeploymentRow deployment =
        store.latestDeployment(id).orElseThrow(() -> new IllegalStateException("任务尚无部署记录"));
    requireEngineJobId(deployment);
    return logRedactor.redact(gateway.logs(deployment.engineJobId(), tail));
  }

  public JsonNode checkpoints(long id) {
    DeploymentRow deployment = latestDeploymentWithJobId(id);
    return gateway.checkpoints(deployment.engineJobId());
  }

  public JsonNode metrics(long id) {
    DeploymentRow deployment = latestDeploymentWithJobId(id);
    return gateway.metrics(deployment.engineJobId());
  }

  public void reconcile() {
    lifecycleLock.lock();
    try {
      List<DefinitionRow> candidates = store.desiredJobs();
      for (DefinitionRow job : candidates) {
        DeploymentRow deployment = store.latestDeployment(job.id()).orElse(null);
        if (deployment == null || !StringUtils.hasText(deployment.engineJobId())) {
          // Another Yak Ops instance may still be inside the synchronous CLI submission. An
          // uncertain submission without a jobId also requires operator verification in Flink UI;
          // it must not be converted into a definite failure by the reconciler.
          if (!"STARTING".equals(job.observedState()) && !"UNKNOWN".equals(job.observedState())) {
            reconcile(job, deployment, new RuntimeStatus(null, RuntimeStatus.State.NONE));
          }
          continue;
        }
        try {
          RuntimeStatus runtime = gateway.status(deployment.engineJobId());
          consecutiveEngineFailures.remove(job.id());
          reconcile(job, deployment, runtime);
        } catch (RealtimeEngineException exception) {
          int failures =
              consecutiveEngineFailures
                  .computeIfAbsent(job.id(), ignored -> new AtomicInteger())
                  .incrementAndGet();
          if (failures >= Math.max(1, properties.getReconcileFailureThreshold())
              && !"UNKNOWN".equals(job.observedState())) {
            changeState(
                job, deployment, "UNKNOWN", "UNKNOWN", deployment.engineJobId(), "Flink 状态不可用");
          }
        }
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  /** Performs an operator-requested reconciliation without submitting a new Flink job. */
  public RealtimeJobView reconcile(long id) {
    lifecycleLock.lock();
    try {
      DefinitionRow definition =
          store.definition(id).orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
      DeploymentRow deployment = latestDeploymentWithJobId(id);
      reconcile(definition, deployment, gateway.status(deployment.engineJobId()));
      consecutiveEngineFailures.remove(id);
      return store.view(id);
    } finally {
      lifecycleLock.unlock();
    }
  }

  private void reconcile(DefinitionRow job, DeploymentRow deployment, RuntimeStatus runtime) {
    Long deploymentId = deployment == null ? null : deployment.id();
    String expectedJobId = deployment == null ? null : deployment.engineJobId();

    if (runtime.state() == RuntimeStatus.State.RUNNING) {
      if (!StringUtils.hasText(expectedJobId)) {
        changeState(job, deployment, "CONFLICT", "UNKNOWN", null, "无法证明 Flink jobId 归属于当前部署");
      } else if (!expectedJobId.equals(runtime.jobId())) {
        changeState(
            job, deployment, "CONFLICT", "UNKNOWN", runtime.jobId(), "Flink jobId 与部署记录不一致");
      } else if ("RUNNING".equals(job.desiredState())) {
        changeState(job, deployment, "RUNNING", "RUNNING", runtime.jobId(), null);
      } else {
        try {
          gateway.stop(expectedJobId);
        } catch (RealtimeEngineException ignored) {
          changeState(job, deployment, "UNKNOWN", "UNKNOWN", runtime.jobId(), "停止状态对账失败");
        }
      }
      return;
    }

    if ("STOPPED".equals(job.desiredState())) {
      changeState(job, deployment, "STOPPED", "STOPPED", expectedJobId, null);
      return;
    }

    String message =
        runtime.state() == RuntimeStatus.State.TERMINATED
            ? "Flink 任务已终止"
            : "Flink 中未找到期望运行的任务；为避免重复回放，不自动重新提交";
    transactions.executeWithoutResult(
        status -> {
          store.markTerminalFailure(job.id(), deploymentId, message);
          store.event(
              job.id(), deploymentId, "FLINK_JOB_LOST", job.observedState(), "FAILED", message);
        });
  }

  private void changeState(
      DefinitionRow job,
      DeploymentRow deployment,
      String observed,
      String deploymentState,
      String engineJobId,
      String error) {
    if (observed.equals(job.observedState())
        && (error == null ? job.lastError() == null : error.equals(job.lastError()))) {
      return;
    }
    stateMachine.requireTransition(job.observedState(), observed);
    transactions.executeWithoutResult(
        status -> {
          store.reconcile(
              job.id(),
              deployment == null ? null : deployment.id(),
              observed,
              deploymentState,
              engineJobId,
              error);
          store.event(
              job.id(),
              deployment == null ? null : deployment.id(),
              "STATE_RECONCILED",
              job.observedState(),
              observed,
              error == null ? "Flink 状态已对账" : error);
        });
  }

  private Prepared prepare(long id, boolean requirePublished) {
    DefinitionRow definition =
        store.definition(id).orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    if (requirePublished) {
      requirePublished(definition);
    }
    CdcPipelineSpec spec = store.spec(definition);
    specValidator.validate(spec);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities();
    capabilityResolver.requireSupported(manifest, resolved, spec);
    return new Prepared(
        definition,
        spec,
        compiler.compile(definition.name(), spec, resolved),
        manifest.path("runtimeVersion").asText("unknown"),
        manifest);
  }

  private void requirePublished(DefinitionRow definition) {
    if (!"PUBLISHED".equals(definition.releaseState())
        || definition.publishedVersion() == null
        || definition.publishedVersion() != definition.definitionVersion()) {
      throw new IllegalStateException("请先发布当前定义版本");
    }
  }

  private void waitForRuntimeStop(String jobId) {
    for (int attempt = 0; attempt < 20; attempt++) {
      RuntimeStatus status = gateway.status(jobId);
      if (status.state() != RuntimeStatus.State.RUNNING) {
        return;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("等待 Flink 停止时被中断", exception);
      }
    }
    throw new IllegalStateException("Flink 任务未在 5 秒内停止，请稍后再重启");
  }

  private void markStopped(long definitionId, DeploymentRow deployment, String message) {
    transactions.executeWithoutResult(
        status -> {
          stateMachine.requireTransition("STOPPING", "STOPPED");
          store.reconcile(
              definitionId,
              deployment == null ? null : deployment.id(),
              "STOPPED",
              "STOPPED",
              deployment == null ? null : deployment.engineJobId(),
              null);
          store.event(
              definitionId,
              deployment == null ? null : deployment.id(),
              "STOPPED",
              "STOPPING",
              "STOPPED",
              message);
        });
  }

  private String normalizeKey(String requestedKey) {
    String key =
        StringUtils.hasText(requestedKey) ? requestedKey.trim() : UUID.randomUUID().toString();
    if (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
      throw new IllegalArgumentException("Idempotency-Key 格式无效");
    }
    return key;
  }

  private RealtimeEngineGateway.DeployResult deploy(Prepared prepared, String key) {
    RealtimeDeployRequest.CredentialBinding[] credentials =
        dataSourceResolver.resolveCredentials(prepared.spec());
    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            prepared.compiled().yaml(), key, credentials[0], credentials[1])) {
      return gateway.deploy(request);
    }
  }

  private DeploymentRow latestDeploymentWithJobId(long id) {
    DeploymentRow deployment =
        store.latestDeployment(id).orElseThrow(() -> new IllegalStateException("任务尚无部署记录"));
    requireEngineJobId(deployment);
    return deployment;
  }

  private void requireEngineJobId(DeploymentRow deployment) {
    if (!StringUtils.hasText(deployment.engineJobId())) {
      throw new IllegalStateException("部署记录尚无 Flink jobId");
    }
  }

  private void requireName(String name) {
    if (!StringUtils.hasText(name) || name.trim().length() > 200) {
      throw new IllegalArgumentException("任务名称不能为空且不能超过 200 个字符");
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法序列化实时同步 Spec", exception);
    }
  }

  private String digest(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算 Spec 摘要", exception);
    }
  }

  private record Prepared(
      DefinitionRow definition,
      CdcPipelineSpec spec,
      CompiledPipeline compiled,
      String runtimeRevision,
      JsonNode manifest) {}
}
