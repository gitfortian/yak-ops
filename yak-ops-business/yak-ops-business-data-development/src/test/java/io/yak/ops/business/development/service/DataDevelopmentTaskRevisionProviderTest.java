package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataDevelopmentTaskRevisionProviderTest {

  @Test
  void resolvesOnlyRevisionOwnedByRequestedDevelopmentNode() {
    DevelopmentTaskRevisionRepository repository = mock(DevelopmentTaskRevisionRepository.class);
    DataDevelopmentTaskRevisionProvider provider = new DataDevelopmentTaskRevisionProvider(repository);
    DevelopmentTaskRevision revision = new DevelopmentTaskRevision(
        101L,
        12L,
        3,
        7L,
        new TaskDefinition("SQL", 1, "select 1", "{}"),
        "checksum",
        Instant.parse("2026-08-12T00:00:00Z"));
    when(repository.findById(101L)).thenReturn(Optional.of(revision));

    assertEquals(TaskAssetSource.DATA_DEVELOPMENT, provider.source());
    assertEquals(3, provider.resolve("12", 101L).orElseThrow().revisionNo());
    assertTrue(provider.resolve("13", 101L).isEmpty());
  }
}
