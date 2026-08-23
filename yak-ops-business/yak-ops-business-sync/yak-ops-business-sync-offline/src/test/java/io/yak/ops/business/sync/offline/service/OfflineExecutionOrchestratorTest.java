package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.service.OfflineExecutionClaimService.ClaimResult;
import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineExecutionOrchestratorTest {

  @Mock private OfflineJobDefinitionService definitionService;
  @Mock private OfflineExecutionClaimService claimService;
  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobExecutionRepository executionRepository;
  @Mock private OfflineBatchExecutionRepository batchRepository;
  @Mock private OfflineExecutionEventRepository eventRepository;
  @Mock private LinkUpClient linkUpClient;

  private OfflineExecutionOrchestrator service;

  @BeforeEach
  void setUp() {
    service = new OfflineExecutionOrchestrator(
        definitionService,
        claimService,
        definitionRepository,
        executionRepository,
        batchRepository,
        eventRepository,
        linkUpClient,
        new ObjectMapper());
  }

  @Test
  void shouldScheduleFailedRetryFromFrozenBatchPolicy() {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);

    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(99L);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(77L);
    execution.setStatus("CREATED");
    execution.setStateVersion(1L);
    execution.setAttemptNo(1);

    when(claimService.claim(10L, "WORKFLOW", null, 1))
        .thenReturn(new ClaimResult(definition, "{}", execution));
    when(definitionService.resolveExecutionJobSpec(definition)).thenReturn("{}");
    when(batchRepository.findById(77L)).thenReturn(Optional.of(frozenBatch()));
    when(definitionRepository.findById(10L)).thenReturn(Optional.of(definition));
    when(linkUpClient.node())
        .thenThrow(new LinkUpTransportException(
            "无法连接 Link-Up Server：http://127.0.0.1:18080",
            new ConnectException(),
            false));

    assertThatThrownBy(() -> service.execute(10L, "WORKFLOW", null, 1))
        .isInstanceOf(LinkUpTransportException.class)
        .hasMessage("无法连接 Link-Up Server：http://127.0.0.1:18080");

    assertThat(execution.getStatus()).isEqualTo("FAILED");
    assertThat(execution.getNextRetryTime()).isNotNull();
    assertThat(execution.getErrorMessage())
        .isEqualTo("无法连接 Link-Up Server：http://127.0.0.1:18080");
    verify(executionRepository, atLeastOnce()).update(execution);
    verify(batchRepository).findById(77L);
    verify(eventRepository, atLeastOnce()).append(any());
  }

  @Test
  void shouldMoveUncertainExecutionToUnknownWithoutRetryTime() {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(99L);
    execution.setJobDefinitionId(10L);
    execution.setBatchId(77L);
    execution.setStatus("RUNNING");
    execution.setStateVersion(3L);
    execution.setAttemptNo(1);
    execution.setNextRetryTime(java.time.LocalDateTime.now().plusMinutes(1));
    execution.setEndTime(java.time.LocalDateTime.now());
    when(definitionRepository.findById(10L)).thenReturn(Optional.empty());

    service.markUnknown(execution, "状态无法确认");

    assertThat(execution.getStatus()).isEqualTo("UNKNOWN");
    assertThat(execution.getNextRetryTime()).isNull();
    assertThat(execution.getEndTime()).isNull();
    verify(executionRepository).update(execution);
    verify(eventRepository).append(any());
  }

  private BatchExecution frozenBatch() {
    return new BatchExecution(
        77L,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 30),
            "digest"),
        BatchStatus.PENDING,
        List.of());
  }
}
