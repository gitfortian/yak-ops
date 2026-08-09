package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionControlRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionIdempotencyRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionClaimService.ClaimResult;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionClaimServiceTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineJobExecutionDao executionDao;
  @Mock private OfflineExecutionControlRepository repository;
  @Mock private OfflineExecutionIdempotencyRepository idempotencyRepository;

  private OfflineExecutionClaimService service;

  @BeforeEach
  void setUp() {
    service = new OfflineExecutionClaimService(
        definitionService,
        executionDao,
        repository,
        idempotencyRepository,
        new OfflineSyncProperties());
  }

  @Test
  void shouldPersistCreatedExecutionBeforeAnyEngineProbe() {
    OfflineJobDefinitionPO definition = new OfflineJobDefinitionPO();
    definition.setId(10L);
    definition.setReleaseState("ONLINE");
    definition.setVersion(3);
    definition.setConfigDigest("digest");
    definition.setDefinitionJson("{}");

    when(definitionService.require(10L)).thenReturn(definition);
    when(definitionService.resolveLogicalJobSpec(definition)).thenReturn("{\"job\":\"spec\"}");
    when(executionDao.insert(any(OfflineJobExecutionPO.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecutionPO execution = invocation.getArgument(0);
          execution.setId(99L);
          return true;
        });

    ClaimResult result = service.claim(10L, "WORKFLOW", null, 1);

    assertThat(result.getExecution().getId()).isEqualTo(99L);
    assertThat(result.getExecution().getStatus()).isEqualTo("CREATED");
    assertThat(result.getExecution().getWorkerInstanceId()).isNull();
    assertThat(result.getExecution().getEngineBaseUrl()).isEqualTo("http://127.0.0.1:18080");
    verify(repository).lockDefinition(10L);
    verify(repository).hasActiveExecution(10L);
    verify(executionDao).insert(result.getExecution());
  }

  @Test
  void shouldPersistWorkflowAttemptAsSnapshotIdempotencyKey() {
    OfflineJobDefinitionPO definition = new OfflineJobDefinitionPO();
    definition.setId(10L);

    when(definitionService.require(10L)).thenReturn(definition);
    when(idempotencyRepository.findByKey("attempt-123")).thenReturn(Optional.empty());
    when(executionDao.insert(any(OfflineJobExecutionPO.class)))
        .thenAnswer(invocation -> {
          OfflineJobExecutionPO execution = invocation.getArgument(0);
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
    assertThat(result.getExecution().getTriggerType()).isEqualTo("WORKFLOW");
    assertThat(result.isReused()).isFalse();
    verify(repository).lockDefinition(10L);
    verify(repository).hasActiveExecution(10L);
    verify(executionDao).insert(result.getExecution());
  }

  @Test
  void shouldReuseSameWorkflowAttemptInsteadOfCreatingAnotherOfflineExecution() {
    OfflineJobDefinitionPO definition = new OfflineJobDefinitionPO();
    definition.setId(10L);
    when(definitionService.require(10L)).thenReturn(definition);

    OfflineJobExecutionPO existing = new OfflineJobExecutionPO();
    existing.setId(101L);
    existing.setJobDefinitionId(10L);
    existing.setDefinitionVersion(3);
    existing.setConfigDigest("digest");
    existing.setDefinitionSnapshotJson("{}");
    existing.setSubmittedConfig("{\"job\":\"spec\"}");
    existing.setIdempotencyKey("attempt-123");
    existing.setStatus("SUBMITTED");
    when(idempotencyRepository.findByKey("attempt-123"))
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
    verify(repository).lockDefinition(10L);
    verify(repository, never()).hasActiveExecution(10L);
    verify(executionDao, never()).insert(any(OfflineJobExecutionPO.class));
  }
}
