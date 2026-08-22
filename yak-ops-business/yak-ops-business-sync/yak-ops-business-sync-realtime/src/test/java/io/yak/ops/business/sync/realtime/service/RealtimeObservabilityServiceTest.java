package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.engine.FlinkObservabilityClient;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeObservabilityServiceTest {

  private static final long JOB_ID = 7L;
  private RealtimeJobStore store;
  private FlinkObservabilityClient flink;
  private RealtimeObservabilityService service;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    flink = mock(FlinkObservabilityClient.class);
    service = new RealtimeObservabilityService(store, flink);
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition()));
  }

  @Test
  void readsSubmissionLogBeforeFlinkJobIdIsRecovered() {
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment(null)));
    when(flink.submissionLog("start-key", 500)).thenReturn("submitting");

    assertThat(service.submissionLog(JOB_ID, 500)).isEqualTo("submitting");
    verify(flink).submissionLog("start-key", 500);
  }

  @Test
  void runtimeObservabilityStillRequiresFlinkJobId() {
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(deployment(null)));

    assertThatThrownBy(() -> service.runtimeLog(JOB_ID, 50))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("尚无 Flink jobId");
  }

  private DefinitionRow definition() {
    LocalDateTime now = LocalDateTime.now();
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        "{}",
        "PUBLISHED",
        "RUNNING",
        "STARTING",
        1,
        1,
        "digest",
        null,
        now,
        now);
  }

  private DeploymentRow deployment(String engineJobId) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        19L,
        JOB_ID,
        1,
        "{}",
        "summary",
        "digest",
        "start-key",
        engineJobId,
        null,
        "SUBMITTING",
        true,
        null,
        now,
        now);
  }
}
