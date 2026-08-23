package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionClaimService.ClaimResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
