package io.yak.ops.business.dataset.lineage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.definition.DatasetReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetLineageRefreshListenerTest {

  @Test
  void refreshLoadsCommittedSnapshotAndSynchronizesLineage() {
    DatasetReader reader = mock(DatasetReader.class);
    DatasetLineageTransactionRunner transactionRunner =
        mock(DatasetLineageTransactionRunner.class);
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(reader, transactionRunner);

    Dataset dataset =
        new Dataset(
            21L, "sales", null, DatasetStatus.ONLINE, null, Instant.EPOCH, Instant.EPOCH);
    DatasetDetail detail = new DatasetDetail(dataset, null, List.of(), List.of());
    when(reader.require(21L)).thenReturn(detail);

    listener.refresh(new DatasetLineageRefreshRequested(21L));

    verify(reader).require(21L);
    verify(transactionRunner).sync(detail);
  }

  @Test
  void lineageFailureDoesNotEscapeAfterDatasetCommit() {
    DatasetReader reader = mock(DatasetReader.class);
    DatasetLineageTransactionRunner transactionRunner =
        mock(DatasetLineageTransactionRunner.class);
    DatasetLineageRefreshListener listener =
        new DatasetLineageRefreshListener(reader, transactionRunner);
    when(reader.require(21L)).thenThrow(new IllegalStateException("lineage read failed"));

    assertDoesNotThrow(() -> listener.refresh(new DatasetLineageRefreshRequested(21L)));
  }
}
