package io.yak.ops.business.sync.offline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.AttemptStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineBatchExecutionRepositoryAdapterTest {

  @Test
  void roundTripsBatchIdentityScopeAndFrozenSnapshot() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository =
        new OfflineBatchExecutionRepositoryAdapter(dao, executions);

    BatchExecution source = new BatchExecution(
        null,
        42L,
        BatchKey.schedule("schedule-42", Instant.parse("2026-08-23T02:00:00Z")),
        BatchTrigger.SCHEDULE,
        BatchScope.partitions(List.of("dt=2026-08-23", "dt=2026-08-22", "dt=2026-08-23")),
        new ExecutionSnapshot(
            "{\"source\":\"orders\"}",
            7,
            new RetryPolicySnapshot(3, 30),
            "digest-7"),
        BatchStatus.PENDING,
        List.of());

    BatchExecution inserted = repository.insert(source);
    BatchExecution reloaded = repository.findById(inserted.id()).orElseThrow();

    assertThat(inserted.id()).isEqualTo(101L);
    assertThat(reloaded.taskId()).isEqualTo(42L);
    assertThat(reloaded.batchKey()).isEqualTo(source.batchKey());
    assertThat(reloaded.trigger()).isEqualTo(BatchTrigger.SCHEDULE);
    assertThat(reloaded.batchScope()).isEqualTo(source.batchScope());
    assertThat(reloaded.batchScope().fingerprint()).isEqualTo(source.batchScope().fingerprint());
    assertThat(reloaded.snapshot()).isEqualTo(source.snapshot());
    assertThat(reloaded.status()).isEqualTo(BatchStatus.PENDING);
    assertThat(reloaded.attempts()).isEmpty();
    assertThat(repository.findByTaskIdAndBatchKey(42L, source.batchKey())).contains(reloaded);
  }

  @Test
  void hydratesBoundLegacyExecutionsAsAttempts() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    OfflineBatchExecutionRepositoryAdapter repository =
        new OfflineBatchExecutionRepositoryAdapter(dao, executions);

    BatchExecution inserted = repository.insert(new BatchExecution(
        null,
        9L,
        BatchKey.manual("request-9"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot("{}", 1, new RetryPolicySnapshot(2, 5), "digest"),
        BatchStatus.RUNNING,
        List.of()));

    OfflineJobExecution legacyAttempt = OfflineJobExecution.builder()
        .id(501L)
        .jobDefinitionId(9L)
        .batchId(inserted.id())
        .attemptNo(1)
        .triggerType("MANUAL")
        .idempotencyKey("attempt-501")
        .externalExecutionId("external-501")
        .status("RUNNING")
        .createTime(LocalDateTime.of(2026, 8, 23, 10, 0))
        .build();
    when(executions.findByBatchId(inserted.id())).thenReturn(List.of(legacyAttempt));

    BatchExecution reloaded = repository.findById(inserted.id()).orElseThrow();

    assertThat(reloaded.attempts()).hasSize(1);
    assertThat(reloaded.attempts().get(0).id()).isEqualTo(501L);
    assertThat(reloaded.attempts().get(0).attemptNo()).isEqualTo(1);
    assertThat(reloaded.attempts().get(0).status()).isEqualTo(AttemptStatus.RUNNING);
  }

  @Test
  void rejectsCorruptedScopeEvidence() {
    FakeBatchDao dao = new FakeBatchDao();
    OfflineJobExecutionRepository executions = mock(OfflineJobExecutionRepository.class);
    when(executions.findByBatchId(anyLong())).thenReturn(List.of());
    OfflineBatchExecutionRepositoryAdapter repository =
        new OfflineBatchExecutionRepositoryAdapter(dao, executions);

    BatchExecution source = new BatchExecution(
        null,
        7L,
        BatchKey.manual("request-7"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot("{}", 1, new RetryPolicySnapshot(1, 0), "digest"),
        BatchStatus.PENDING,
        List.of());

    BatchExecution inserted = repository.insert(source);
    dao.stored.setBatchScopeFingerprint("corrupted");

    assertThatThrownBy(() -> repository.findById(inserted.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fingerprint");
  }

  private static final class FakeBatchDao implements OfflineBatchExecutionDao {
    private OfflineBatchExecutionPO stored;

    @Override
    public OfflineBatchExecutionPO selectById(Long id) {
      return stored != null && stored.getId().equals(id) ? stored : null;
    }

    @Override
    public OfflineBatchExecutionPO selectByTaskIdAndBatchKey(Long taskId, String batchKey) {
      return stored != null
              && stored.getJobDefinitionId().equals(taskId)
              && stored.getBatchKey().equals(batchKey)
          ? stored
          : null;
    }

    @Override
    public boolean insert(OfflineBatchExecutionPO batchPO) {
      batchPO.setId(101L);
      stored = batchPO;
      return true;
    }

    @Override
    public boolean updateById(OfflineBatchExecutionPO batchPO) {
      stored = batchPO;
      return true;
    }
  }
}
