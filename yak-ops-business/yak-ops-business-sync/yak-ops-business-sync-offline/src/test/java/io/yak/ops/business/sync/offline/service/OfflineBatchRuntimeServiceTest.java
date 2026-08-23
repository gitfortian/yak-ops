package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.compat.LegacyOfflineExecutionCompatibilityMapper;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OfflineBatchRuntimeServiceTest {

  @Test
  void latestAttemptIsTheOnlySourceOfBatchRuntimeTruth() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntimeService service = new OfflineBatchRuntimeService(batches, attempts);

    BatchExecution batch = batch(77L, BatchStatus.PENDING, List.of());
    OfflineJobExecution oldSucceeded = attempt(501L, 77L, 1, "SUCCEEDED");
    OfflineJobExecution latestRunning = attempt(502L, 77L, 2, "RUNNING");
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(oldSucceeded, latestRunning));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    service.refreshBatch(77L);

    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.RUNNING);
  }

  @Test
  void failedAttemptWithRetryReservationWindowMeansWaitingRetry() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntimeService service = new OfflineBatchRuntimeService(batches, attempts);

    BatchExecution batch = batch(77L, BatchStatus.RUNNING, List.of());
    OfflineJobExecution failed = attempt(501L, 77L, 1, "FAILED");
    failed.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(failed));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    service.refreshBatch(77L);

    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.WAITING_RETRY);
  }

  @Test
  void persistsAttemptBeforeRefreshingBatchTruth() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntimeService service = new OfflineBatchRuntimeService(batches, attempts);

    BatchExecution batch = batch(77L, BatchStatus.PENDING, List.of());
    OfflineJobExecution running = attempt(501L, 77L, 1, "RUNNING");
    when(attempts.update(running)).thenReturn(true);
    when(batches.findById(77L)).thenReturn(Optional.of(batch));
    when(attempts.findByBatchId(77L)).thenReturn(List.of(running));
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    service.persistAttempt(running);

    InOrder order = inOrder(attempts, batches);
    order.verify(attempts).update(running);
    order.verify(batches).findById(77L);
    order.verify(attempts).findByBatchId(77L);
    order.verify(batches).update(any(BatchExecution.class));
  }

  @Test
  void unknownAttemptKeepsBatchInUnknownInsteadOfFailed() {
    OfflineBatchRuntimeService service = new OfflineBatchRuntimeService(
        mock(OfflineBatchExecutionRepository.class),
        mock(OfflineJobExecutionRepository.class));

    OfflineJobExecution unknown = attempt(501L, 77L, 1, "UNKNOWN");

    assertThat(service.deriveStatus(unknown)).isEqualTo(BatchStatus.UNKNOWN);
  }

  @Test
  void cancelWaitingRetryUsesSameCasReservationAsAutomaticRetry() {
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineJobExecutionRepository attempts = mock(OfflineJobExecutionRepository.class);
    OfflineBatchRuntimeService service = new OfflineBatchRuntimeService(batches, attempts);

    OfflineJobExecution failed = attempt(501L, 77L, 1, "FAILED");
    failed.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
    BatchExecution waiting = batch(
        77L,
        BatchStatus.WAITING_RETRY,
        List.of(LegacyOfflineExecutionCompatibilityMapper.toAttempt(failed)));
    when(attempts.findById(501L)).thenReturn(Optional.of(failed));
    when(attempts.reserveRetry(501L)).thenReturn(true);
    when(batches.update(any(BatchExecution.class))).thenReturn(true);

    OfflineJobExecution result = service.cancelWaitingRetry(waiting);

    assertThat(result).isSameAs(failed);
    assertThat(failed.getNextRetryTime()).isNull();
    assertThat(failed.getRetryCreated()).isTrue();
    verify(attempts).reserveRetry(501L);
    ArgumentCaptor<BatchExecution> captured = ArgumentCaptor.forClass(BatchExecution.class);
    verify(batches).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(BatchStatus.CANCELED);
  }

  private BatchExecution batch(
      long id,
      BatchStatus status,
      List<io.yak.ops.business.sync.offline.domain.core.ExecutionAttempt> attempts) {
    return new BatchExecution(
        id,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot("{}", 1, new RetryPolicySnapshot(3, 30), "digest"),
        status,
        attempts);
  }

  private OfflineJobExecution attempt(long id, long batchId, int attemptNo, String status) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(batchId);
    execution.setAttemptNo(attemptNo);
    execution.setTriggerType(attemptNo == 1 ? "MANUAL" : "RETRY");
    execution.setIdempotencyKey("attempt-" + id);
    execution.setExternalExecutionId("external-" + id);
    execution.setStatus(status);
    execution.setRetryCreated(false);
    execution.setCreateTime(LocalDateTime.of(2026, 8, 23, 10, attemptNo));
    return execution;
  }
}
