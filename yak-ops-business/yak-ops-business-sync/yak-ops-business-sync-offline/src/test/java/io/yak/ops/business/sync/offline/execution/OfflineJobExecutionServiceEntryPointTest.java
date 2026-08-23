package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionLogService;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionReadService;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import org.junit.jupiter.api.Test;

class OfflineJobExecutionServiceEntryPointTest {

  @Test
  void occupancyQueryDelegatesToBatchRuntimeTruth() {
    Fixture fixture = fixture();
    when(fixture.runtime.hasOccupyingBatch(10L)).thenReturn(true);

    assertThat(fixture.service.hasOccupyingBatch(10L)).isTrue();

    verify(fixture.runtime).hasOccupyingBatch(10L);
  }

  @Test
  void scheduledExecutionPreservesScheduleTriggerToken() {
    Fixture fixture = fixture();
    String triggerToken = "SCHEDULE|schedule-10|2026-08-23T08:00:00";
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(21L);
    OfflineJobExecutionVO view = mock(OfflineJobExecutionVO.class);

    when(fixture.orchestrator.execute(10L, triggerToken, null, 1)).thenReturn(execution);
    when(fixture.readService.toVO(execution)).thenReturn(view);

    assertThat(fixture.service.executeScheduled(10L, triggerToken)).isSameAs(view);

    verify(fixture.orchestrator).execute(10L, triggerToken, null, 1);
    verify(fixture.readService).toVO(execution);
  }

  @Test
  void pendingBackfillExecutionDelegatesToOrchestrator() {
    Fixture fixture = fixture();
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(31L);
    OfflineJobExecutionVO view = mock(OfflineJobExecutionVO.class);

    when(fixture.orchestrator.executePendingBackfill(77L)).thenReturn(execution);
    when(fixture.readService.toVO(execution)).thenReturn(view);

    assertThat(fixture.service.executePendingBackfill(77L)).isSameAs(view);

    verify(fixture.orchestrator).executePendingBackfill(77L);
    verify(fixture.readService).toVO(execution);
  }

  private Fixture fixture() {
    OfflineExecutionOrchestrator orchestrator = mock(OfflineExecutionOrchestrator.class);
    OfflineBatchRuntimeService runtime = mock(OfflineBatchRuntimeService.class);
    OfflineExecutionReadService readService = mock(OfflineExecutionReadService.class);
    OfflineExecutionLogService logService = mock(OfflineExecutionLogService.class);
    LinkUpClient linkUpClient = mock(LinkUpClient.class);
    OfflineSyncViewMapper viewMapper = mock(OfflineSyncViewMapper.class);
    return new Fixture(
        new OfflineJobExecutionService(
            orchestrator,
            runtime,
            readService,
            logService,
            linkUpClient,
            viewMapper),
        orchestrator,
        runtime,
        readService);
  }

  private record Fixture(
      OfflineJobExecutionService service,
      OfflineExecutionOrchestrator orchestrator,
      OfflineBatchRuntimeService runtime,
      OfflineExecutionReadService readService) {}
}
