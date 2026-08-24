package io.yak.ops.business.dataset.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.ExecutionResult;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetQueryCoordinatorTest {

  @Test
  void currentQueryUsesExactCurrentVersionAndRecordsPerformanceEvidence() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(repository, registry, recorder);

    Dataset dataset = dataset(21L, 32L);
    DatasetVersion current = version(32L, 21L, 2, DatasetSourceType.SQL_QUERY);
    List<DatasetField> fields = List.of();
    DatasetQueryResult rawResult =
        new DatasetQueryResult(21L, 32L, 2, List.of(), List.of(), List.of(), 0, false, 7L);
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(32L)).thenReturn(Optional.of(current));
    when(repository.listFields(32L)).thenReturn(fields);
    when(registry.require(DatasetSourceType.SQL_QUERY)).thenReturn(adapter);
    when(adapter.execute(dataset, current, fields, null))
        .thenReturn(new ExecutionResult(rawResult, "ds-1", "select 1", 1L, 2L, 3L, 4L));

    DatasetQueryResult result = coordinator.query(21L, null);

    assertNotNull(result.queryId());
    assertEquals(32L, result.datasetVersionId());
    verify(adapter).execute(dataset, current, fields, null);
    ArgumentCaptor<DatasetQueryPerformance> trace =
        ArgumentCaptor.forClass(DatasetQueryPerformance.class);
    verify(recorder).record(trace.capture());
    assertEquals(32L, trace.getValue().datasetVersionId());
    assertEquals(2, trace.getValue().datasetVersionNo());
  }

  @Test
  void requestedVersionDoesNotDriftToCurrentPointer() {
    DatasetRepository repository = mock(DatasetRepository.class);
    DatasetSourceQueryRegistry registry = mock(DatasetSourceQueryRegistry.class);
    DatasetQueryPerformanceRecorder recorder = mock(DatasetQueryPerformanceRecorder.class);
    DatasetSourceQueryAdapter adapter = mock(DatasetSourceQueryAdapter.class);
    DatasetQueryCoordinator coordinator =
        new DatasetQueryCoordinator(repository, registry, recorder);

    Dataset dataset = dataset(21L, 32L);
    DatasetVersion v1 = version(31L, 21L, 1, DatasetSourceType.QUERY_REVISION);
    DatasetVersion v2 = version(32L, 21L, 2, DatasetSourceType.SQL_QUERY);
    DatasetQueryRequest request =
        new DatasetQueryRequest(1, List.of(), List.of(), List.of(), List.of(), 10, null);
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.listVersions(21L)).thenReturn(List.of(v2, v1));
    when(repository.listFields(31L)).thenReturn(List.of());
    when(registry.require(DatasetSourceType.QUERY_REVISION)).thenReturn(adapter);
    when(adapter.execute(any(), any(), any(), any()))
        .thenReturn(
            new ExecutionResult(
                new DatasetQueryResult(
                    21L, 31L, 1, List.of(), List.of(), List.of(), 0, false, 1L),
                "ds-1",
                "select 1",
                0L,
                0L,
                1L,
                0L));

    DatasetQueryResult result = coordinator.query(21L, request);

    assertEquals(31L, result.datasetVersionId());
    verify(registry).require(DatasetSourceType.QUERY_REVISION);
    verify(adapter).execute(dataset, v1, List.of(), request);
  }

  private Dataset dataset(long datasetId, long currentVersionId) {
    return new Dataset(
        datasetId,
        "sales",
        null,
        DatasetStatus.ONLINE,
        currentVersionId,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private DatasetVersion version(
      long versionId, long datasetId, int versionNo, DatasetSourceType sourceType) {
    return new DatasetVersion(
        versionId,
        datasetId,
        versionNo,
        sourceType,
        sourceType == DatasetSourceType.QUERY_REVISION ? 11L : 0L,
        sourceType == DatasetSourceType.QUERY_REVISION ? 71L : 0L,
        sourceType == DatasetSourceType.QUERY_REVISION ? 3 : 0,
        sourceType == DatasetSourceType.SQL_QUERY ? "ds-1" : null,
        sourceType == DatasetSourceType.SQL_QUERY ? "select 1" : null,
        "[]",
        Instant.EPOCH);
  }
}
