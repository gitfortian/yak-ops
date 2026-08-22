package io.yak.ops.business.sync.realtime.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.RealtimeStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeJobLifecycleCoordinatorTest {

  private static final long JOB_ID = 7L;
  private static final long DEPLOYMENT_ID = 19L;
  private static final String FLINK_JOB_ID = "0123456789abcdef0123456789abcdef";

  private RealtimeJobStore store;
  private RealtimeRuntimeIdentityStore identityStore;
  private FlinkJobDiscoveryClient discovery;
  private RealtimeEngineGateway gateway;
  private RealtimeJobLifecycleCoordinator coordinator;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    identityStore = mock(RealtimeRuntimeIdentityStore.class);
    discovery = mock(FlinkJobDiscoveryClient.class);
    gateway = mock(RealtimeEngineGateway.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    coordinator =
        new RealtimeJobLifecycleCoordinator(
            store,
            identityStore,
            discovery,
            gateway,
            new RealtimeStateMachine(),
            10,
            transactionManager);
  }

  @Test
  void recoversMissingJobIdThenReconcilesRunning() {
    DefinitionRow definition = definition("RUNNING", "UNKNOWN");
    DeploymentRow deployment = deployment(null, "UNKNOWN", LocalDateTime.now());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition));
    when(store.lockDefinition(JOB_ID)).thenReturn(definition);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment));
    when(identityStore.findByDeploymentId(DEPLOYMENT_ID)).thenReturn(Optional.of("yak-rt-test"));
    when(discovery.findJobIds("yak-rt-test")).thenReturn(List.of(FLINK_JOB_ID));
    when(gateway.status(FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.RUNNING));

    coordinator.reconcile(JOB_ID);

    verify(store)
        .reconcile(JOB_ID, DEPLOYMENT_ID, "UNKNOWN", "UNKNOWN", FLINK_JOB_ID, null);
    verify(store)
        .reconcile(JOB_ID, DEPLOYMENT_ID, "RUNNING", "RUNNING", FLINK_JOB_ID, null);
    verify(store)
        .event(
            JOB_ID,
            DEPLOYMENT_ID,
            "FLINK_JOB_ID_RECOVERED",
            "UNKNOWN",
            "UNKNOWN",
            "已通过 runtime job identity 找回 Flink JobId：" + FLINK_JOB_ID);
  }

  @Test
  void confirmsStoppedOnlyAfterRecoveryWindowAndNoMatchingFlinkJob() {
    DefinitionRow definition = definition("STOPPED", "STOPPING");
    DeploymentRow deployment =
        deployment(null, "UNKNOWN", LocalDateTime.now().minusMinutes(5));
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition));
    when(store.lockDefinition(JOB_ID)).thenReturn(definition);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment));
    when(identityStore.findByDeploymentId(DEPLOYMENT_ID)).thenReturn(Optional.of("yak-rt-test"));
    when(discovery.findJobIds("yak-rt-test")).thenReturn(List.of());

    coordinator.reconcile(JOB_ID);

    verify(store).reconcile(JOB_ID, DEPLOYMENT_ID, "STOPPED", "STOPPED", null, null);
    verify(store)
        .event(
            JOB_ID,
            DEPLOYMENT_ID,
            "STOPPED",
            "STOPPING",
            "STOPPED",
            "恢复窗口内未发现匹配的 Flink runtime job，已确认停止");
  }

  private DefinitionRow definition(String desired, String observed) {
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        "{}",
        "PUBLISHED",
        desired,
        observed,
        1,
        1,
        "digest",
        null,
        null,
        null);
  }

  private DeploymentRow deployment(
      String engineJobId, String status, LocalDateTime createTime) {
    return new DeploymentRow(
        DEPLOYMENT_ID,
        JOB_ID,
        1,
        "{}",
        "summary",
        "digest",
        "key",
        engineJobId,
        null,
        status,
        true,
        null,
        createTime,
        createTime);
  }
}
