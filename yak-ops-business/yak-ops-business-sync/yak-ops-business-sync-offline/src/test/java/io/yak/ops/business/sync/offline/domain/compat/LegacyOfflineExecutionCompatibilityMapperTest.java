package io.yak.ops.business.sync.offline.domain.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptReason;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LegacyOfflineExecutionCompatibilityMapperTest {

  @Test
  void lostLegacyExecutionMapsToUnknownRetryAttempt() {
    OfflineJobExecution legacy =
        OfflineJobExecution.builder()
            .id(20L)
            .definitionVersion(3)
            .engineJobId("engine-job-20")
            .externalExecutionId("external-20")
            .idempotencyKey("idem-20")
            .workerInstanceId("worker-a")
            .status("LOST")
            .attemptNo(2)
            .triggerType("RETRY")
            .retryFromExecutionId(10L)
            .sourceRecordCount(100L)
            .sinkSuccessRecordCount(90L)
            .failedRecordCount(10L)
            .qps(25D)
            .durationMillis(5000L)
            .createTime(LocalDateTime.of(2026, 8, 23, 10, 0))
            .build();

    ExecutionAttempt attempt = LegacyOfflineExecutionCompatibilityMapper.toAttempt(legacy);

    assertThat(attempt.id()).isEqualTo(20L);
    assertThat(attempt.attemptNo()).isEqualTo(2);
    assertThat(attempt.reason()).isEqualTo(AttemptReason.RETRY);
    assertThat(attempt.status()).isEqualTo(AttemptStatus.UNKNOWN);
    assertThat(attempt.engineExecutionRef().jobId()).isEqualTo("engine-job-20");
    assertThat(attempt.metrics().sourceRecordCount()).isEqualTo(100L);
    assertThat(attempt.metrics().failedRecordCount()).isEqualTo(10L);
  }

  @Test
  void snapshotRequiresExplicitFrozenRetryPolicyAndLogicalJobSpec() {
    OfflineJobExecution legacy =
        OfflineJobExecution.builder()
            .definitionVersion(7)
            .definitionSnapshotJson("{\"source\":{\"table\":\"orders\"}}")
            .configDigest("digest-7")
            .submittedConfig("{\"kind\":\"BatchSyncJob\"}")
            .build();
    RetryPolicySnapshot retryPolicy = new RetryPolicySnapshot(4, 30);

    ExecutionSnapshot snapshot =
        LegacyOfflineExecutionCompatibilityMapper.toSnapshot(legacy, retryPolicy);

    assertThat(snapshot.definitionRevision()).isEqualTo(7);
    assertThat(snapshot.definitionSnapshot()).contains("orders");
    assertThat(snapshot.retryPolicy()).isSameAs(retryPolicy);
    assertThat(snapshot.configDigest()).isEqualTo("digest-7");
    assertThat(snapshot.logicalJobSpec()).contains("BatchSyncJob");
  }

  @Test
  void snapshotMapperNeverFallsBackWhenFrozenEvidenceIsMissing() {
    OfflineJobExecution legacy =
        OfflineJobExecution.builder()
            .definitionVersion(1)
            .definitionSnapshotJson("{\"source\":{}}")
            .configDigest("digest")
            .submittedConfig("{}")
            .build();

    assertThatThrownBy(() -> LegacyOfflineExecutionCompatibilityMapper.toSnapshot(legacy, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("retryPolicy");
  }
}
