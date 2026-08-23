package io.yak.ops.business.sync.offline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OfflineExecutionStatusTest {

  @Test
  void shouldExposeOfflineBatchLifecycleIncludingUnknown() {
    assertThat(OfflineExecutionStatus.CREATED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.SUBMITTED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.QUEUED.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.RUNNING.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.UNKNOWN.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.LOST.isActive()).isTrue();
    assertThat(OfflineExecutionStatus.SUCCEEDED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.FAILED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.CANCELED.isTerminal()).isTrue();
    assertThat(OfflineExecutionStatus.UNKNOWN.isTerminal()).isFalse();
    assertThat(OfflineExecutionStatus.LOST.isTerminal()).isFalse();
  }

  @Test
  void shouldTreatMissingPersistedStatusAsInactive() {
    assertThat(OfflineExecutionStatus.isActive(null)).isFalse();
    assertThat(OfflineExecutionStatus.isActive("")).isFalse();
    assertThat(OfflineExecutionStatus.isActive("   ")).isFalse();
  }

  @Test
  void shouldNormalizeLegacyStatusesWithoutAddingRealtimeStates() {
    assertThat(OfflineExecutionStatus.parse("FINISHED"))
        .isEqualTo(OfflineExecutionStatus.SUCCEEDED);
    assertThat(OfflineExecutionStatus.parse("CANCELLING"))
        .isEqualTo(OfflineExecutionStatus.CANCELED);
    assertThat(OfflineExecutionStatus.parse("LOST"))
        .isEqualTo(OfflineExecutionStatus.UNKNOWN);
  }
}
