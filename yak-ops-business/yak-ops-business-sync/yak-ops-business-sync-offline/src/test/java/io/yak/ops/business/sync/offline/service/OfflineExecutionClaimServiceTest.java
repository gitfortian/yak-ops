package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionClaimService.ClaimResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionClaimServiceTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineScheduleRepository scheduleRepository;

  private OfflineExecutionClaimService service;

  @BeforeEach
  void setUp() {
    service = new OfflineExecutionClaimService(
        definitionService,
        definitionRepository,
        executionRepository,
        batchRepository,
        scheduleRepository,
        new OfflineSyncProperties());
  }

  @Test
  void shouldPersistCreatedExecutionBeforeAnyEngineProbe() {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setReleaseState("ONLINE");
    definition.setVersion(3);
    definition.setConfigDigest("digest");
    definition.setDefinitionJson("{}");

    when(definitionService.require(10L)).thenReturn(definition);
    when(definitionService.resolveLogicalJobSpec(definition)).thenReturn("{\"job\":\"spec\"}");
    stubBatchInsert(77L);
    when(executionRepository.insert(any(OfflineJobExecution.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecution execution = invocation.getArgument(0);
          execution.setId(99L);
          return true;
        });

    ClaimResult result = service.claim(10L, "WORKFLOW", null, 1);

    assertThat(result.getExecution().getId()).isEqualTo(99L);
    assertThat(result.getExecution().getBatchId()).isEqualTo(77L);
    assertThat(result.getExecution().getStatus()).isEqualTo("CREATED");
    assertThat(result.getExecution().getWorkerInstanceId()).isNull();
    assertThat(result.getExecution().getEngineBaseUrl()).isEqualTo("http://127.0.0.1:18080");
    verify(definitionRepository).lock(10L);
    verify(executionRepository).hasActiveExecution(10L);
    verify(batchRepository).insert(any(BatchExecution.class));
    verify(executionRepository).insert(result.getExecution());
  }

  @Test
  void shouldPersistWorkflowAttemptAsSnapshotIdempotencyKey() {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    when(definitionService.require(10L)).thenReturn(definition);
    when(executionRepository.findByIdempotencyKey("attempt-123")).thenReturn(Optional.empty());
    stubBatchInsert(78L);
    when(executionRepository.insert(any(OfflineJobExecution.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecution execution = invocation.getArgument(0);
          execution.setId(100L);
          return true;
        });

    ClaimResult result = service.claimSnapshot(
        10L,
        3L,
        "digest",
        "{}",
        "{\"job\":\"spec\"}",
        "WORKFLOW",
        "attempt-123");

    assertThat(result.getExecution().getIdempotencyKey()).isEqualTo("attempt-123");
    assertThat(result.getExecution().getBatchId()).isEqualTo(78L);
    assertThat(result.getExecution().getTriggerType()).isEqualTo("WORKFLOW");
    assertThat(result.isReused()).isFalse();
    verify(definitionRepository).lock(10L);
    verify(executionRepository).hasActiveExecution(10L);
    verify(batchRepository).insert(any(BatchExecution.class));
    verify(executionRepository).insert(result.getExecution());
  }

  @Test
  void shouldReuseSameWorkflowAttemptInsteadOfCreatingAnotherOfflineExecution() {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    when(definitionService.require(10L)).thenReturn(definition);

    OfflineJobExecution existing = new OfflineJobExecution();
    existing.setId(101L);
    existing.setJobDefinitionId(10L);
    existing.setDefinitionVersion(3);
    existing.setConfigDigest("digest");
    existing.setDefinitionSnapshotJson("{}");
    existing.setSubmittedConfig("{\"job\":\"spec\"}");
    existing.setIdempotencyKey("attempt-123");
    existing.setStatus("SUBMITTED");
    when(executionRepository.findByIdempotencyKey("attempt-123"))
        .thenReturn(Optional.of(existing));

    ClaimResult result = service.claimSnapshot(
        10L,
        3L,
        "digest",
        "{}",
        "{\"job\":\"spec\"}",
        "WORKFLOW",
        "attempt-123");

    assertThat(result.getExecution()).isSameAs(existing);
    assertThat(result.isReused()).isTrue();
    verify(definitionRepository).lock(10L);
    verify(executionRepository, never()).hasActiveExecution(10L);
    verify(batchRepository, never()).insert(any(BatchExecution.class));
    verify(executionRepository, never()).insert(any(OfflineJobExecution.class));
  }

  @Test
  void shouldCreateRetryFromFrozenBatchWithoutReadingCurrentTaskOrSchedule() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1, "{\"job\":\"frozen\"}");
    BatchExecution batch = frozenBatch(77L, 3);
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(previous));
    when(executionRepository.reserveRetry(99L)).thenReturn(true);
    when(executionRepository.insert(any(OfflineJobExecution.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecution execution = invocation.getArgument(0);
          execution.setId(100L);
          return true;
        });

    ClaimResult result = service.claimRetry(99L);
    OfflineJobExecution retry = result.getExecution();

    assertThat(retry.getId()).isEqualTo(100L);
    assertThat(retry.getBatchId()).isEqualTo(77L);
    assertThat(retry.getAttemptNo()).isEqualTo(2);
    assertThat(retry.getRetryFromExecutionId()).isEqualTo(99L);
    assertThat(retry.getTriggerType()).isEqualTo("RETRY");
    assertThat(retry.getDefinitionVersion()).isEqualTo(3);
    assertThat(retry.getConfigDigest()).isEqualTo("frozen-digest");
    assertThat(retry.getDefinitionSnapshotJson()).isEqualTo("{\"definition\":\"frozen\"}");
    assertThat(retry.getSubmittedConfig()).isEqualTo("{\"job\":\"frozen\"}");
    assertThat(retry.getIdempotencyKey()).isEqualTo("offline-retry:77:2");
    assertThat(result.isReused()).isFalse();

    InOrder order = inOrder(executionRepository);
    order.verify(executionRepository).reserveRetry(99L);
    order.verify(executionRepository).insert(retry);
    verifyNoInteractions(definitionService, definitionRepository, scheduleRepository);
  }

  @Test
  void shouldRejectUnknownWithoutBlindRetry() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1, "{\"job\":\"frozen\"}");
    previous.setStatus("UNKNOWN");
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(frozenBatch(77L, 3)));

    assertThatThrownBy(() -> service.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNKNOWN")
        .hasMessageContaining("reconcile");

    verify(executionRepository, never()).reserveRetry(99L);
    verify(executionRepository, never()).insert(any(OfflineJobExecution.class));
  }

  @Test
  void shouldRespectFrozenRetryMaxAttempts() {
    OfflineJobExecution previous = failedAttempt(99L, 77L, 1, "{\"job\":\"frozen\"}");
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(frozenBatch(77L, 1)));
    when(executionRepository.findByBatchId(77L)).thenReturn(List.of(previous));

    assertThatThrownBy(() -> service.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("最大 Attempt");

    verify(executionRepository, never()).reserveRetry(99L);
  }

  @Test
  void shouldRejectLegacyRetryWithoutBatchIdentity() {
    OfflineJobExecution previous = failedAttempt(99L, null, 1, "{\"job\":\"legacy\"}");
    when(executionRepository.findById(99L)).thenReturn(Optional.of(previous));

    assertThatThrownBy(() -> service.claimRetry(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未绑定 Batch");
  }

  private OfflineJobExecution failedAttempt(
      Long id,
      Long batchId,
      int attemptNo,
      String submittedConfig) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(id);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(batchId);
    execution.setAttemptNo(attemptNo);
    execution.setStatus("FAILED");
    execution.setSubmittedConfig(submittedConfig);
    execution.setRetryCreated(false);
    return execution;
  }

  private BatchExecution frozenBatch(long id, int maxAttempts) {
    return new BatchExecution(
        id,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{\"definition\":\"frozen\"}",
            3,
            new RetryPolicySnapshot(maxAttempts, 30),
            "frozen-digest"),
        BatchStatus.PENDING,
        List.of());
  }

  private void stubBatchInsert(long id) {
    when(batchRepository.insert(any(BatchExecution.class)))
        .thenAnswer(invocation -> {
          BatchExecution batch = invocation.getArgument(0);
          return new BatchExecution(
              id,
              batch.taskId(),
              batch.batchKey(),
              batch.trigger(),
              batch.batchScope(),
              batch.snapshot(),
              batch.status(),
              batch.attempts());
        });
  }
}
