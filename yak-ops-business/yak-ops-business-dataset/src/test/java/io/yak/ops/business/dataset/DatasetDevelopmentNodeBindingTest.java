package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetDevelopmentNodeBindingTest {

  @Test
  void datasetNodeCreatesStableIdentityBoundToExactSqlRevision() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog, new ObjectMapper());

    TaskAsset asset = taskAsset(11L, 71L, 3);
    Dataset dataset = new Dataset(
        21L, "sales_dataset", "sales", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    when(catalog.get(11L)).thenReturn(asset);
    when(repository.findDatasetByDevelopmentNodeId(501L)).thenReturn(Optional.empty());
    when(repository.insertDevelopmentNodeDataset(501L, "sales_dataset", "sales")).thenReturn(21L);
    when(repository.nextVersionNo(21L)).thenReturn(1);
    when(repository.insertVersion(
        eq(21L), eq(1), eq(DatasetSourceType.QUERY_REVISION), eq(11L), eq(71L), eq(3),
        eq("[]"))).thenReturn(31L);
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(repository.listVersions(21L)).thenReturn(List.of(version));
    when(repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result = service.saveForDevelopmentNode(
        501L,
        new DatasetService.PublishCommand(11L, "sales_dataset", "sales", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    assertEquals(71L, result.currentVersion().sourceTaskRevisionId());
    assertEquals(3, result.currentVersion().sourceTaskRevisionNo());
    verify(repository).insertDevelopmentNodeDataset(501L, "sales_dataset", "sales");
    verify(repository).updateCurrentVersion(21L, 31L);
    verify(repository).insertFields(eq(31L), anyList());
  }

  @Test
  void savingSameRevisionAndSchemaOnlyUpdatesMutableMetadata() {
    DatasetRepository repository = mock(DatasetRepository.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DatasetService service = new DatasetService(repository, catalog, new ObjectMapper());

    TaskAsset asset = taskAsset(11L, 71L, 3);
    Dataset dataset = new Dataset(
        21L, "sales_dataset", "before", DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    when(catalog.get(11L)).thenReturn(asset);
    when(repository.findDatasetByDevelopmentNodeId(501L)).thenReturn(Optional.of(dataset));
    when(repository.findDataset(21L)).thenReturn(Optional.of(dataset));
    when(repository.findVersion(31L)).thenReturn(Optional.of(version));
    when(repository.listVersions(21L)).thenReturn(List.of(version));
    when(repository.listFields(31L)).thenReturn(List.of());

    DatasetDetail result = service.saveForDevelopmentNode(
        501L,
        new DatasetService.PublishCommand(11L, "sales_dataset", "after", List.of()));

    assertEquals(1, result.currentVersion().versionNo());
    verify(repository).updateMetadata(21L, "sales_dataset", "after");
    verify(repository, never()).insertVersion(
        eq(21L), eq(2), eq(DatasetSourceType.QUERY_REVISION), eq(11L), eq(71L), eq(3),
        eq("[]"));
  }

  private static TaskAsset taskAsset(long assetId, long revisionId, int revisionNo) {
    return new TaskAsset(
        assetId,
        TaskAssetSource.DATA_DEVELOPMENT,
        "101",
        null,
        "sales.sql",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(assetId, revisionId, revisionNo),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
