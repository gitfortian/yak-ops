package io.yak.ops.business.lineage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LineageMaintenanceServiceTest {

  @Test
  void replacementCapturesEndpointsThenUsesOneOwnershipGuardedBulkDelete() {
    LineageRepository repository = mock(LineageRepository.class);
    when(repository.findAssetIdsByEvidence("PARSE", "task-1"))
        .thenReturn(Set.of(1L, 2L, 3L));
    LineageMaintenanceService service = new LineageMaintenanceService(repository);

    LineageMaintenanceService.CleanupScope scope =
        service.beginReplacement("PARSE", "task-1", "TASK", "task-1");
    service.finishReplacement(scope);

    InOrder order = inOrder(repository);
    order.verify(repository).findAssetIdsByEvidence("PARSE", "task-1");
    order.verify(repository).deleteRelationsByEvidence("PARSE", "task-1");
    order.verify(repository).deleteUnreferencedOwnedAssets(Set.of(1L, 2L, 3L), "TASK", "task-1");
  }

  @Test
  void revisionLockRejectsStalePublisherAndAcceptsCurrentPublisher() throws Exception {
    LineageRepository repository = mock(LineageRepository.class);
    LineageMaintenanceService service = new LineageMaintenanceService(repository);
    LineageAsset asset = new LineageAsset(1, "task:key", LineageAssetType.SQL_TASK, "task",
        "TASK", "1", null, null, null, null, null, null,
        new ObjectMapper().readTree("{\"revisionNo\":8}"), Instant.EPOCH, Instant.EPOCH);
    when(repository.lockAssetByKey("task:key")).thenReturn(Optional.of(asset));

    assertFalse(service.lockAndAcceptRevision("task:key", 7));
    assertTrue(service.lockAndAcceptRevision("task:key", 8));
    verify(repository, org.mockito.Mockito.times(2)).lockAssetByKey("task:key");
  }
}
