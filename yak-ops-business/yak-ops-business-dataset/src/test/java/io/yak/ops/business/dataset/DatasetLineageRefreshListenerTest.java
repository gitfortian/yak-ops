package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetLineageRefreshListenerTest {

  @Test
  void refreshLoadsCommittedSnapshotAndSynchronizesLineage() {
    DatasetService datasetService = mock(DatasetService.class);
    DatasetLineageService lineageService = mock(DatasetLineageService.class);
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(datasetService, lineageService);

    Dataset dataset = new Dataset(
        21L, "sales", null, DatasetStatus.ONLINE, null, Instant.EPOCH, Instant.EPOCH);
    DatasetDetail detail = new DatasetDetail(dataset, null, List.of(), List.of());
    when(datasetService.get(21L)).thenReturn(detail);

    listener.refresh(new DatasetLineageRefreshRequested(21L));

    verify(datasetService).get(21L);
    verify(lineageService).syncCurrent(detail);
  }

  @Test
  void lineageFailureDoesNotEscapeAfterDatasetCommit() {
    DatasetService datasetService = mock(DatasetService.class);
    DatasetLineageService lineageService = mock(DatasetLineageService.class);
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(datasetService, lineageService);

    when(datasetService.get(21L)).thenThrow(new IllegalStateException("lineage read failed"));

    assertDoesNotThrow(() -> listener.refresh(new DatasetLineageRefreshRequested(21L)));
  }
}
