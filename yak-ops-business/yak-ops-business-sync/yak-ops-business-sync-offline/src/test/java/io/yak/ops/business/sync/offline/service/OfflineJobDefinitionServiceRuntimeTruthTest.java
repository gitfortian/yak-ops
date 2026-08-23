package io.yak.ops.business.sync.offline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleLifecycle;
import io.yak.ops.business.sync.offline.service.support.OfflineScheduleSupport;
import io.yak.ops.business.sync.offline.service.support.OfflineSyncViewMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineJobDefinitionServiceRuntimeTruthTest {

  @Test
  void offlineUsesBatchTruthAndIgnoresStaleTaskStatusProjection() {
    OfflineJobDefinitionRepository definitions = mock(OfflineJobDefinitionRepository.class);
    OfflineBatchRuntimeService runtime = mock(OfflineBatchRuntimeService.class);
    OfflineScheduleRepository schedules = mock(OfflineScheduleRepository.class);
    OfflineScheduleLifecycle lifecycle = mock(OfflineScheduleLifecycle.class);
    OfflineJobDefinitionService service = service(definitions, runtime, schedules, lifecycle);

    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setReleaseState("ONLINE");
    definition.setLastJobStatus("RUNNING");
    when(definitions.findById(10L)).thenReturn(Optional.of(definition));
    when(runtime.hasOccupyingBatch(10L)).thenReturn(false);
    when(definitions.update(definition)).thenReturn(true);

    assertThat(service.offline(10L)).isTrue();

    assertThat(definition.getReleaseState()).isEqualTo("OFFLINE");
    verify(runtime).hasOccupyingBatch(10L);
    verify(lifecycle).sync(10L);
  }

  @Test
  void deleteIsBlockedByOccupyingBatchEvenWhenTaskProjectionLooksTerminal() {
    OfflineJobDefinitionRepository definitions = mock(OfflineJobDefinitionRepository.class);
    OfflineBatchRuntimeService runtime = mock(OfflineBatchRuntimeService.class);
    OfflineScheduleRepository schedules = mock(OfflineScheduleRepository.class);
    OfflineScheduleLifecycle lifecycle = mock(OfflineScheduleLifecycle.class);
    OfflineJobDefinitionService service = service(definitions, runtime, schedules, lifecycle);

    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setReleaseState("OFFLINE");
    definition.setLastJobStatus("SUCCEEDED");
    when(definitions.findById(10L)).thenReturn(Optional.of(definition));
    when(runtime.hasOccupyingBatch(10L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(10L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("BatchExecution");

    verify(runtime).hasOccupyingBatch(10L);
    verify(lifecycle, never()).remove(10L);
  }

  private OfflineJobDefinitionService service(
      OfflineJobDefinitionRepository definitions,
      OfflineBatchRuntimeService runtime,
      OfflineScheduleRepository schedules,
      OfflineScheduleLifecycle lifecycle) {
    return new OfflineJobDefinitionService(
        definitions,
        runtime,
        schedules,
        mock(OfflineDefinitionSupport.class),
        mock(OfflineScheduleSupport.class),
        lifecycle,
        mock(OfflineSyncViewMapper.class));
  }
}
