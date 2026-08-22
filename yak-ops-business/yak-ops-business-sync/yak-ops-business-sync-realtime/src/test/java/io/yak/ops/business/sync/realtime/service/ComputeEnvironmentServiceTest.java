package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.FlinkRuntimeEnvironmentProbe;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ComputeEnvironmentServiceTest {

  private ComputeEnvironmentStore store;
  private FlinkRuntimeEnvironmentProbe probe;
  private ComputeEnvironmentService service;

  @BeforeEach
  void setUp() {
    store = mock(ComputeEnvironmentStore.class);
    probe = mock(FlinkRuntimeEnvironmentProbe.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service = new ComputeEnvironmentService(store, probe, transactionManager);
  }

  @Test
  void createsSshEnvironmentWithNormalizedConnectionSettings() {
    when(store.insert(any(), any(), any(), any(), any(), eq(true), eq(false))).thenReturn(11L);
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            "/usr/lib/jvm/java-17",
            "1.20.5",
            "3.6.0",
            new SshConfig(
                " ssh ",
                "10.0.0.30",
                22,
                "flink",
                " C:\\keys\\flink_ed25519 ",
                null,
                true,
                8,
                "flink-jm.internal",
                8081));

    long id = service.create("生产 SSH 环境", "ssh", config, true, false);

    assertThat(id).isEqualTo(11L);
    ArgumentCaptor<RuntimeConfig> captured = ArgumentCaptor.forClass(RuntimeConfig.class);
    verify(store)
        .insert(
            eq("生产 SSH 环境"),
            eq(ComputeEnvironment.ENGINE_FLINK_CDC),
            eq(ComputeEnvironment.DEPLOYMENT_REMOTE),
            eq(ComputeEnvironment.SUBMITTER_SSH),
            captured.capture(),
            eq(true),
            eq(false));
    RuntimeConfig saved = captured.getValue();
    assertThat(saved.ssh().executable()).isEqualTo("ssh");
    assertThat(saved.ssh().identityFile()).isEqualTo("C:\\keys\\flink_ed25519");
    assertThat(saved.ssh().remoteRestAddress()).isEqualTo("flink-jm.internal");
  }

  @Test
  void rejectsSshEnvironmentWithoutSubmissionHost() {
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig("ssh", null, 22, "flink", null, null, true, 5, null, null));

    assertThatThrownBy(() -> service.create("bad", "SSH", config, true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SSH Host");
  }

  @Test
  void rejectsWindowsRuntimePathForRemoteLinuxSubmission() {
    RuntimeConfig config =
        new RuntimeConfig(
            "http://10.0.0.20:8081",
            "C:\\flink",
            "C:\\flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig("ssh", "10.0.0.30", 22, "flink", null, null, true, 5, null, null));

    assertThatThrownBy(() -> service.create("bad-path", "SSH", config, true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Linux 绝对路径");
  }

  @Test
  void persistsDiagnosisSummaryForSavedEnvironment() {
    LocalDateTime checkedAt = LocalDateTime.of(2026, 8, 22, 10, 0);
    ComputeEnvironment environment =
        new ComputeEnvironment(
            11L,
            "生产实时环境",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://flink:8081", "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
            true,
            false,
            3,
            checkedAt.minusDays(1),
            checkedAt.minusDays(1));
    ComputeEnvironmentDiagnosis diagnosis =
        new ComputeEnvironmentDiagnosis(
            11L,
            environment.name(),
            ComputeEnvironmentDiagnosis.STATUS_HEALTHY,
            true,
            "环境检查通过，可以提交实时同步任务",
            "1.20.5",
            "3.6.0",
            "17.0.12",
            checkedAt,
            List.of());
    when(store.find(11L)).thenReturn(Optional.of(environment));
    when(probe.diagnose(any(ComputeEnvironmentSnapshot.class))).thenReturn(diagnosis);

    ComputeEnvironmentDiagnosis result = service.diagnose(11L);

    assertThat(result).isEqualTo(diagnosis);
    verify(store)
        .saveDiagnosis(
            11L,
            ComputeEnvironmentDiagnosis.STATUS_HEALTHY,
            "环境检查通过，可以提交实时同步任务",
            checkedAt);
  }
}
