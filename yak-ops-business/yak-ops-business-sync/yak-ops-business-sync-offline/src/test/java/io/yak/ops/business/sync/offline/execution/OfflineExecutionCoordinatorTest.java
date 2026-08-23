package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.execution.adapter.OfflineBatchScopeExecutionAdapter;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionCoordinatorTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineExecutionClaimManager claimManager;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineBatchRuntime batchRuntime;
  @Mock private OfflineBatchScopeExecutionAdapter scopeExecutionAdapter;
  @Mock private OfflineExecutionStateManager stateManager;
  @Mock private LinkUpClient linkUpClient;

  private OfflineExecutionCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new OfflineExecutionCoordinator(
        definitionService,
        claimManager,
        executionRepository,
        batchRepository,
        batchRuntime,
        scopeExecutionAdapter,
        stateManager,
        linkUpClient,
        new ObjectMapper());
  }

  @Test
  void transportFailureDelegatesStateDecisionWithoutOwningRetryPolicy() {
    OfflineJobExecution execution = execution(99L, "CREATED");
    BatchExecution batch = frozenBatch(BatchStatus.RUNNING, BatchScope.fullSelection());
    when(claimManager.claim(10L, "WORKFLOW", null, 1))
        .thenReturn(new OfflineExecutionClaim(null, "{}", execution));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(scopeExecutionAdapter.apply(10L, "{}", batch.batchScope())).thenReturn("{}");
    when(definitionService.resolveExecutionJobSpec("{}")).thenReturn("{}");
    when(linkUpClient.node())
        .thenThrow(
            new LinkUpTransportException(
                "无法连接 Link-Up Server：http://127.0.0.1:18080",
                new ConnectException(),
                false));

    assertThatThrownBy(() -> coordinator.execute(10L, "WORKFLOW", null, 1))
        .isInstanceOf(LinkUpTransportException.class);

    verify(stateManager).recordCreated(execution);
    verify(stateManager)
        .markFailed(execution, "无法连接 Link-Up Server：http://127.0.0.1:18080", true);
    verify(scopeExecutionAdapter).apply(10L, "{}", batch.batchScope());
  }

  @Test
  void retryKeepsOriginalBatchScopeAtSubmissionBoundary() {
    OfflineJobExecution previous = execution(98L, "FAILED");
    OfflineJobExecution retry = execution(99L, "CREATED");
    retry.setAttemptNo(2);
    BatchScope.CursorRange range = BatchScope.cursorRange("orders", "100", "200");
    BatchExecution batch = frozenBatch(BatchStatus.RUNNING, range);
    when(claimManager.claimRetry(98L))
        .thenReturn(new OfflineExecutionClaim(null, "logical", retry));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(scopeExecutionAdapter.apply(10L, "logical", range)).thenReturn("scoped");
    when(definitionService.resolveExecutionJobSpec("scoped")).thenReturn("{}");
    when(linkUpClient.node())
        .thenThrow(new LinkUpTransportException("engine down", new ConnectException(), false));

    assertThatThrownBy(() -> coordinator.retryFrom(previous))
        .isInstanceOf(LinkUpTransportException.class);

    verify(scopeExecutionAdapter).apply(10L, "logical", range);
    verify(stateManager).markFailed(retry, "engine down", true);
  }

  @Test
  void reusedSubmittedBackfillDoesNotSubmitAgain() {
    OfflineJobExecution existing = execution(99L, "SUBMITTED");
    when(claimManager.claimPendingBackfill(77L))
        .thenReturn(new OfflineExecutionClaim(null, "logical", existing, true));

    assertThat(coordinator.executePendingBackfill(77L)).isSameAs(existing);

    verifyNoInteractions(scopeExecutionAdapter, linkUpClient, stateManager);
  }

  @Test
  void waitingRetryCancelUsesBatchTruthThenDelegatesProjection() {
    BatchExecution waiting = frozenBatch(BatchStatus.WAITING_RETRY, BatchScope.fullSelection());
    OfflineJobExecution latest = execution(99L, "FAILED");
    when(batchRuntime.requireLatestOccupyingBatch(10L)).thenReturn(waiting);
    when(batchRuntime.cancelWaitingRetry(waiting)).thenReturn(latest);

    assertThat(coordinator.cancelLatestBatch(10L)).isSameAs(latest);

    verify(batchRuntime).requireLatestOccupyingBatch(10L);
    verify(batchRuntime).cancelWaitingRetry(waiting);
    verify(stateManager).markWaitingRetryCanceled(latest);
  }

  @Test
  void batchlessLegacyExecutionIsHistoryOnlyAndCannotBeCanceled() {
    OfflineJobExecution history = execution(99L, "RUNNING");
    history.setBatchId(null);
    when(executionRepository.findById(99L)).thenReturn(Optional.of(history));

    assertThatThrownBy(() -> coordinator.cancel(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未绑定 Batch")
        .hasMessageContaining("仅支持查询");

    verify(stateManager, never()).markCancellationRequested(history);
    verifyNoInteractions(linkUpClient);
  }

  @Test
  void reconcilerStateEntrypointsDelegateToStateManager() {
    OfflineJobExecution execution = execution(99L, "RUNNING");
    LinkUpJobResponse response = new LinkUpJobResponse();

    coordinator.applySnapshot(execution, response, "RECONCILED");
    coordinator.markUnknown(execution, "状态无法确认");

    verify(stateManager).applySnapshot(execution, response, "RECONCILED");
    verify(stateManager).markUnknown(execution, "状态无法确认");
  }

  private OfflineJobExecution execution(long id, String status) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(77L);
    execution.setStatus(status);
    execution.setStateVersion(1L);
    execution.setAttemptNo(1);
    return execution;
  }

  private BatchExecution frozenBatch(BatchStatus status, BatchScope scope) {
    return new BatchExecution(
        77L,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        scope,
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 30),
            "digest",
            "logical"),
        status,
        List.of());
  }
}
